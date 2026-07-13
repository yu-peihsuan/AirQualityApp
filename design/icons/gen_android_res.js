// 產生 Android 圖示資源：7 組 × (傳統 mipmap 5 密度 + 自適應前景 5 密度 + 背景漸層 XML + anydpi-v26 XML)
const sharp = require("sharp");
const fs = require("fs");
const path = require("path");

const RES = path.join(__dirname, "../../app/src/main/res");
const SRC = path.join(__dirname, "masters");
const INK = "#2E4A5A";

const cloud = (color) => `
  <g fill="${color}">
    <circle cx="196" cy="286" r="82"/>
    <circle cx="298" cy="252" r="98"/>
    <circle cx="376" cy="304" r="68"/>
    <rect x="146" y="286" width="230" height="92" rx="46"/>
  </g>`;
const MASK = `
  <path d="M244 296 Q206 290 190 270" fill="none" stroke="#C2D3DE" stroke-width="7" stroke-linecap="round"/>
  <path d="M360 296 Q398 290 414 270" fill="none" stroke="#C2D3DE" stroke-width="7" stroke-linecap="round"/>
  <rect x="244" y="274" width="116" height="62" rx="24" fill="#F4F9FC" stroke="#C2D3DE" stroke-width="5"/>
  <path d="M260 296 H344 M260 314 H344" stroke="#C2D3DE" stroke-width="5" stroke-linecap="round" fill="none"/>`;
const eyeDots = (y) => `
  <ellipse cx="266" cy="${y}" rx="11" ry="14" fill="${INK}"/>
  <ellipse cx="340" cy="${y}" rx="11" ry="14" fill="${INK}"/>`;

const happyFace = `
  <g fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round">
    <path d="M252 262 q15 -16 30 0"/>
    <path d="M326 262 q15 -16 30 0"/>
    <path d="M282 294 q22 20 44 0"/>
  </g>
  <ellipse cx="243" cy="298" rx="21" ry="14" fill="#FFA48E" opacity=".9"/>
  <ellipse cx="365" cy="298" rx="21" ry="14" fill="#FFA48E" opacity=".9"/>`;

const icons = {
  default: {
    src: "default_icon.png", grad: ["#A8E0F0", "#6BCBB6"], cloud: "#FFFFFF", face: happyFace,
  },
  aqi0: {
    src: "AQI0.png", grad: ["#A0DCA0", "#57BA8C"], cloud: "#FFFFFF", face: happyFace,
  },
  aqi51: {
    src: "AQI51.png", grad: ["#F7E18C", "#E8BA5A"], cloud: "#F0EFEB",
    face: `${eyeDots(256)}
      <path d="M280 300 h44" fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round"/>`,
  },
  aqi101: {
    src: "AQI101.png", grad: ["#F7C58C", "#E69A5A"], cloud: "#DBD8D2",
    face: `<g fill="none" stroke="${INK}" stroke-width="10" stroke-linecap="round">
        <path d="M242 232 l38 10"/><path d="M364 232 l-38 10"/></g>
      ${eyeDots(262)}
      <path d="M284 308 q19 -16 38 0" fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round"/>`,
  },
  aqi151: {
    src: "AQI151.png", grad: ["#F49E93", "#E07369"], cloud: "#BFBBB4",
    face: `${MASK}
      <g fill="none" stroke="${INK}" stroke-width="10" stroke-linecap="round">
        <path d="M242 228 l38 10"/><path d="M364 228 l-38 10"/></g>
      ${eyeDots(254)}`,
  },
  aqi201: {
    src: "AQI201.png", grad: ["#C2A0E2", "#9A72CC"], cloud: "#A19C95",
    face: `${MASK}
      <g fill="none" stroke="${INK}" stroke-width="10" stroke-linecap="round">
        <path d="M286 244 a20 20 0 1 0 -40 0 a14 14 0 1 0 28 0 a7 7 0 1 0 -14 0"/>
        <path d="M360 244 a20 20 0 1 0 -40 0 a14 14 0 1 0 28 0 a7 7 0 1 0 -14 0"/>
      </g>`,
  },
  aqi301: {
    src: "AQI301.png", grad: ["#BB808C", "#8E5968"], cloud: "#696560",
    face: `${MASK}
      <g fill="none" stroke="${INK}" stroke-width="11" stroke-linecap="round">
        <path d="M250 242 l30 26 M280 242 l-30 26"/>
        <path d="M326 242 l30 26 M356 242 l-30 26"/>
      </g>`,
  },
};

const LEGACY = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
const LAYER  = { mdpi: 108, hdpi: 162, xhdpi: 216, xxhdpi: 324, xxxhdpi: 432 };

(async () => {
  for (const [name, cfg] of Object.entries(icons)) {
    const base = `ic_cloud_${name}`;

    // 1) 傳統方形圖示（API 24/25 fallback）：從已輸出的 512 縮圖
    for (const [dpi, size] of Object.entries(LEGACY)) {
      const dir = path.join(RES, `mipmap-${dpi}`);
      fs.mkdirSync(dir, { recursive: true });
      await sharp(path.join(SRC, cfg.src)).resize(size, size).png().toFile(path.join(dir, `${base}.png`));
    }

    // 2) 自適應前景層（透明底，雲寶置中在安全區內）
    const fgSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 432 432" width="432" height="432">
      <g transform="translate(-0.5,9.6) scale(.776)">
        ${cloud(cfg.cloud)}
        ${cfg.face}
      </g>
    </svg>`;
    for (const [dpi, size] of Object.entries(LAYER)) {
      const dir = path.join(RES, `mipmap-${dpi}`);
      await sharp(Buffer.from(fgSvg)).resize(size, size).png().toFile(path.join(dir, `${base}_fg.png`));
    }

    // 3) 背景漸層 drawable
    const bgXml = `<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="315"
        android:startColor="${cfg.grad[0]}"
        android:endColor="${cfg.grad[1]}"
        android:type="linear" />
</shape>
`;
    fs.writeFileSync(path.join(RES, "drawable", `bg_cloud_${name}.xml`), bgXml);

    // 4) 自適應圖示定義（API 26+）
    const adaptiveXml = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/bg_cloud_${name}" />
    <foreground android:drawable="@mipmap/${base}_fg" />
</adaptive-icon>
`;
    fs.writeFileSync(path.join(RES, "mipmap-anydpi-v26", `${base}.xml`), adaptiveXml);

    console.log(`✔ ${base}（5 密度方形 + 5 密度前景 + bg + adaptive）`);
  }
  console.log("done");
})();
