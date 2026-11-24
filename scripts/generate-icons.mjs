import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);
  const typeBuffer = Buffer.from(type, "ascii");
  const body = Buffer.concat([typeBuffer, data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([length, body, crc]);
}

function roundedSquare(x, y, size, radius) {
  const cx = Math.min(Math.max(x, radius), size - radius);
  const cy = Math.min(Math.max(y, radius), size - radius);
  const dx = x - cx;
  const dy = y - cy;
  return dx * dx + dy * dy <= radius * radius;
}

function circle(x, y, size) {
  const c = size / 2;
  const r = size / 2;
  return (x - c) ** 2 + (y - c) ** 2 <= r * r;
}

function makePng(size, round = false) {
  const rowBytes = size * 4;
  const raw = Buffer.alloc((rowBytes + 1) * size);
  const border = size * 0.045;

  const bg = [0x67, 0x50, 0xa4, 0xff];
  const white = [0xff, 0xff, 0xff, 0xff];
  const green = [0x4c, 0xaf, 0x50, 0xff];

  const bx1 = size * 0.25;
  const bx2 = size * 0.70;
  const by1 = size * 0.33;
  const by2 = size * 0.67;
  const stroke = size * 0.045;
  const termX1 = bx2 + size * 0.015;
  const termX2 = bx2 + size * 0.085;
  const termY1 = size * 0.44;
  const termY2 = size * 0.56;

  for (let y = 0; y < size; y += 1) {
    const rowStart = y * (rowBytes + 1);
    raw[rowStart] = 0;

    for (let x = 0; x < size; x += 1) {
      const offset = rowStart + 1 + x * 4;
      let color = [0, 0, 0, 0];

      const inBackground = round
        ? circle(x + 0.5, y + 0.5, size)
        : roundedSquare(x + 0.5, y + 0.5, size, size * 0.22);

      if (inBackground) {
        color = bg;

        const inBody = x >= bx1 && x <= bx2 && y >= by1 && y <= by2;
        const inInner = x > bx1 + stroke && x < bx2 - stroke && y > by1 + stroke && y < by2 - stroke;

        if (inBody) {
          color = inInner ? green : white;
        }

        if (x >= termX1 && x <= termX2 && y >= termY1 && y <= termY2) {
          color = white;
        }
      }

      raw[offset] = color[0];
      raw[offset + 1] = color[1];
      raw[offset + 2] = color[2];
      raw[offset + 3] = color[3];
    }
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", zlib.deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

const outputs = [
  ["web/public/icon-192.png", makePng(192)],
  ["web/public/icon-512.png", makePng(512)],
  ["android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png", makePng(192)],
  ["android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png", makePng(192, true)],
];

for (const [relativePath, buffer] of outputs) {
  const output = path.resolve(relativePath);
  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, buffer);
  console.log(`Wrote ${output}`);
}
