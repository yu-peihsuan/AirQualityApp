// 雲寶圖示母檔產生器：SVG 組裝 → 512x512 PNG（輸出至 masters/）
// 設計定稿 2026-07-12：滿版構圖、雲身隨等級由白轉灰、中飽和 AQI 背景色
const sharp = require("sharp");
const fs = require("fs");
const path = require("path");

const OUT = path.join(__dirname, "masters");
fs.mkdirSync(OUT, { recursive: true });

const INK = "#2E4A5A";

// 統一雲身（顏色由各等級指定：空氣越差雲越髒）
const cloud = (color) => `
  <g fill="${color}">
    <circle cx="196" cy="286" r="82"/>
    <circle cx="298" cy="252" r="98"/>
    <circle cx="376" cy="304" r="68"/>
    <rect x="146" y="286" width="230" height="92" rx="46"/>
  </g>`;

// 口罩（淺灰口罩繩版）
const MASK = `
  <path d="M244 296 Q206 290 190 270" fill="none" stroke="#C2D3DE" stroke-width="7" stroke-linecap="round"/>
  <path d="M360 296 Q398 290 414 270" fill="none" stroke="#C2D3DE" stroke-width="7" stroke-linecap="round"/>
  <rect x="244" y="274" width="116" height="62" rx="24" fill="#F4F9FC" stroke="#C2D3DE" stroke-width="5"/>
  <path d="M260 296 H344 M260 314 H344" stroke="#C2D3DE" stroke-width="5" stroke-linecap="round" fill="none"/>`;

const eyeDots = (y) => `
  <ellipse cx="266" cy="${y}" rx="11" ry="14" fill="${INK}"/>
  <ellipse cx="340" cy="${y}" rx="11" ry="14" fill="${INK}"/>`;

const HAPPY_FACE = `
  <g fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round">
    <path d="M252 262 q15 -16 30 0"/>
    <path d="M326 262 q15 -16 30 0"/>
    <path d="M282 294 q22 20 44 0"/>
  </g>
  <ellipse cx="243" cy="298" rx="21" ry="14" fill="#FFA48E" opacity=".9"/>
  <ellipse cx="365" cy="298" rx="21" ry="14" fill="#FFA48E" opacity=".9"/>`;

const icons = {
  // 預設圖示（品牌版）
  default_icon: {
    grad: ["#A8E0F0", "#6BCBB6"], cloud: "#FFFFFF",
    face: HAPPY_FACE,
  },
  // AQI 0-50 良好：瞇眼笑＋大腮紅（純白雲）
  AQI0: {
    grad: ["#A0DCA0", "#57BA8C"], cloud: "#FFFFFF",
    face: HAPPY_FACE,
  },
  // AQI 51-100 普通：點眼＋直線嘴（米白雲）
  AQI51: {
    grad: ["#F7E18C", "#E8BA5A"], cloud: "#F0EFEB",
    face: `
      ${eyeDots(256)}
      <path d="M280 300 h44" fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round"/>`,
  },
  // AQI 101-150 敏感族群：生氣眉＋嘟嘴（淺灰雲）
  AQI101: {
    grad: ["#F7C58C", "#E69A5A"], cloud: "#DBD8D2",
    face: `
      <g fill="none" stroke="${INK}" stroke-width="10" stroke-linecap="round">
        <path d="M242 232 l38 10"/>
        <path d="M364 232 l-38 10"/>
      </g>
      ${eyeDots(262)}
      <path d="M284 308 q19 -16 38 0" fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round"/>`,
  },
  // AQI 151-200 不健康：生氣眉＋口罩（中灰雲）
  AQI151: {
    grad: ["#F49E93", "#E07369"], cloud: "#BFBBB4",
    face: `
      ${MASK}
      <g fill="none" stroke="${INK}" stroke-width="10" stroke-linecap="round">
        <path d="M242 228 l38 10"/>
        <path d="M364 228 l-38 10"/>
      </g>
      ${eyeDots(254)}`,
  },
  // AQI 201-300 非常不健康：旋渦眼＋口罩（深灰雲）
  AQI201: {
    grad: ["#C2A0E2", "#9A72CC"], cloud: "#A19C95",
    face: `
      ${MASK}
      <g fill="none" stroke="${INK}" stroke-width="10" stroke-linecap="round">
        <path d="M286 244 a20 20 0 1 0 -40 0 a14 14 0 1 0 28 0 a7 7 0 1 0 -14 0"/>
        <path d="M360 244 a20 20 0 1 0 -40 0 a14 14 0 1 0 28 0 a7 7 0 1 0 -14 0"/>
      </g>`,
  },
  // AQI 301+ 危害：XX眼＋口罩（煙灰雲）
  AQI301: {
    grad: ["#BB808C", "#8E5968"], cloud: "#696560",
    face: `
      ${MASK}
      <g fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round">
        <path d="M250 242 l30 26 M280 242 l-30 26"/>
        <path d="M326 242 l30 26 M356 242 l-30 26"/>
      </g>`,
  },
};

(async () => {
  for (const [name, cfg] of Object.entries(icons)) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
      <defs>
        <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="${cfg.grad[0]}"/>
          <stop offset="1" stop-color="${cfg.grad[1]}"/>
        </linearGradient>
      </defs>
      <rect width="512" height="512" fill="url(#bg)"/>
      <g transform="translate(-190,-70) scale(1.6)">
        ${cloud(cfg.cloud)}
        ${cfg.face}
      </g>
    </svg>`;
    await sharp(Buffer.from(svg)).png().toFile(path.join(OUT, `${name}.png`));
    console.log(`✔ ${name}.png`);
  }
  console.log("done →", OUT);
})();
