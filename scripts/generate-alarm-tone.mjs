import fs from "node:fs";
import path from "node:path";

const sampleRate = 22050;
const beepSeconds = 0.18;
const gapSeconds = 0.12;
const beeps = 3;
const frequency = 880;

const totalSeconds = beeps * beepSeconds + (beeps - 1) * gapSeconds;
const sampleCount = Math.floor(sampleRate * totalSeconds);
const dataSize = sampleCount * 2;

const buffer = Buffer.alloc(44 + dataSize);

buffer.write("RIFF", 0, "ascii");
buffer.writeUInt32LE(36 + dataSize, 4);
buffer.write("WAVE", 8, "ascii");
buffer.write("fmt ", 12, "ascii");
buffer.writeUInt32LE(16, 16);
buffer.writeUInt16LE(1, 20);
buffer.writeUInt16LE(1, 22);
buffer.writeUInt32LE(sampleRate, 24);
buffer.writeUInt32LE(sampleRate * 2, 28);
buffer.writeUInt16LE(2, 32);
buffer.writeUInt16LE(16, 34);
buffer.write("data", 36, "ascii");
buffer.writeUInt32LE(dataSize, 40);

for (let i = 0; i < sampleCount; i += 1) {
  const time = i / sampleRate;
  const pattern = beepSeconds + gapSeconds;
  const withinPattern = time % pattern;
  const active = withinPattern < beepSeconds;
  const envelope = active ? Math.min(1, withinPattern / 0.015) * Math.min(1, (beepSeconds - withinPattern) / 0.025) : 0;
  const value = active ? Math.sin(2 * Math.PI * frequency * time) * envelope * 0.45 : 0;
  buffer.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(value * 32767))), 44 + i * 2);
}

const output = path.resolve("android/app/src/main/res/raw/alarm_tone.wav");
fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, buffer);
console.log(`Wrote ${output}`);
