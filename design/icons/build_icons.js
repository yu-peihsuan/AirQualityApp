// 雲寶六等級圖示：SVG 組裝 → 512x512 PNG
const sharp = require("sharp");
const fs = require("fs");
const path = require("path");

const OUT = path.join(__dirname, "masters");
fs.mkdirSync(OUT, { recursive: true });

const INK = "#2E4A5A";

// 統一雲身
const CLOUD = `
  <g fill="#FFFFFF">
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

// 「AQI」字樣（向量筆畫，白字＋深色柔影；縮小 68%、水平置中）
const AQI_LETTERS = `
  <g transform="translate(82,47) scale(.68)" fill="none" stroke-linecap="round" stroke-linejoin="round">
    <g stroke="rgba(30,50,62,.30)" stroke-width="18" transform="translate(4,5)">
      <path d="M175 128 L199 64 L223 128"/>
      <path d="M185 106 H213"/>
      <circle cx="272" cy="94" r="34"/>
      <path d="M285 116 L303 134"/>
      <path d="M337 64 V128"/>
    </g>
    <g stroke="#FFFFFF" stroke-width="18">
      <path d="M175 128 L199 64 L223 128"/>
      <path d="M185 106 H213"/>
      <circle cx="272" cy="94" r="34"/>
      <path d="M285 116 L303 134"/>
      <path d="M337 64 V128"/>
    </g>
  </g>`;

const icons = {
  // 預設圖示（品牌版）：大笑＋腮紅，品牌漸層底
  default_icon: {
    grad: ["#BCE6F2", "#82D2C0"],
    face: `
      <g fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round">
        <path d="M252 262 q15 -16 30 0"/>
        <path d="M326 262 q15 -16 30 0"/>
        <path d="M282 294 q22 20 44 0"/>
      </g>
      <ellipse cx="243" cy="298" rx="21" ry="14" fill="#FFA48E" opacity=".9"/>
      <ellipse cx="365" cy="298" rx="21" ry="14" fill="#FFA48E" opacity=".9"/>`,
  },
  // AQI 0-50 良好：瞇眼笑＋大腮紅
  AQI0: {
    grad: ["#B4E2B0", "#72C69C"],
    face: `
      <g fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round">
        <path d="M252 262 q15 -16 30 0"/>
        <path d="M326 262 q15 -16 30 0"/>
        <path d="M282 294 q22 20 44 0"/>
      </g>
      <ellipse cx="243" cy="298" rx="21" ry="14" fill="#FFA48E" opacity=".9"/>
      <ellipse cx="365" cy="298" rx="21" ry="14" fill="#FFA48E" opacity=".9"/>`,
  },
  // AQI 51-100 普通：點眼＋直線嘴（無腮紅）
  AQI51: {
    grad: ["#F8E6A4", "#ECC776"],
    face: `
      ${eyeDots(256)}
      <path d="M280 300 h44" fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round"/>`,
  },
  // AQI 101-150 敏感族群：生氣眉＋嘟嘴
  AQI101: {
    grad: ["#F8D2A6", "#ECAB74"],
    face: `
      <g fill="none" stroke="${INK}" stroke-width="10" stroke-linecap="round">
        <path d="M242 232 l38 10"/>
        <path d="M364 232 l-38 10"/>
      </g>
      ${eyeDots(262)}
      <path d="M284 308 q19 -16 38 0" fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round"/>`,
  },
  // AQI 151-200 不健康：生氣眉＋口罩
  AQI151: {
    grad: ["#F5B3AA", "#E68A80"],
    face: `
      ${MASK}
      <g fill="none" stroke="${INK}" stroke-width="10" stroke-linecap="round">
        <path d="M242 228 l38 10"/>
        <path d="M364 228 l-38 10"/>
      </g>
      ${eyeDots(254)}`,
  },
  // AQI 201-300 非常不健康：旋渦眼＋口罩
  AQI201: {
    grad: ["#CFB4E8", "#AC8CD6"],
    face: `
      ${MASK}
      <g fill="none" stroke="${INK}" stroke-width="10" stroke-linecap="round">
        <path d="M286 244 a20 20 0 1 0 -40 0 a14 14 0 1 0 28 0 a7 7 0 1 0 -14 0"/>
        <path d="M360 244 a20 20 0 1 0 -40 0 a14 14 0 1 0 28 0 a7 7 0 1 0 -14 0"/>
      </g>`,
  },
  // AQI 301+ 危害：XX眼＋口罩
  AQI301: {
    grad: ["#C595A0", "#9E717E"],
    face: `
      ${MASK}
      <g fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round">
        <path d="M250 242 l30 26 M280 242 l-30 26"/>
        <path d="M326 242 l30 26 M356 242 l-30 26"/>
      </g>`,
  },
};

(async () => {
  for (const [name, { grad, face }] of Object.entries(icons)) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
      <defs>
        <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="${grad[0]}"/>
          <stop offset="1" stop-color="${grad[1]}"/>
        </linearGradient>
      </defs>
      <rect width="512" height="512" fill="url(#bg)"/>
      <g transform="translate(-190,-70) scale(1.6)">
        ${CLOUD}
        ${face}
      </g>
    </svg>`;
    const out = path.join(OUT, `${name}.png`);
    await sharp(Buffer.from(svg)).png().toFile(out);
    console.log(`✔ ${name}.png`);
  }
  console.log("done →", OUT);
})();
