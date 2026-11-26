# Stella TD

เกม Tower Defense บนมือถือ (Android แท้ ไม่ใช่เว็บ) ป้องกันฐานคริสตัลจากอุกกาบาตและหลุมดำด้วยดวงดาว

## มีอะไรในเกม
- 4 ด่าน: วงโคจรแรก (15 เวฟ), แถบดาวเคราะห์น้อย (20), เนบิวลาวน (25), ใจกลางหลุมดำ (30) ผ่านด่านหนึ่งแล้วด่านถัดไปจะปลดล็อก
- เริ่มเกมด้วย 5 ดาว ได้เพิ่มทุกเวฟ และได้จาก Satellite / Mars ที่ผลิตดาว
- ดวงดาว 12 แบบ: Earth, Satellite, Moon (เริ่มต้น) / Saturn, Jupiter (Rare) / Mars (Epic) / Mercury, Venus, Uranus, Neptune, Pluto (Legend) / Sun (Mythical)
- Enchant แบบสุ่ม 10 แบบ มีสัญลักษณ์ที่มุมของดวงดาว: Damage/Range/Cooldown I–III, Gold, Blitz, Froze, Burn, Moon, Gigantic, Megelinia
- Effect: Gold, Diamond, Elite, Bigger (เห็นเป็นออร่า เพชร มงกุฎ หรือดาวที่ใหญ่ขึ้น)
- ศัตรู 9 แบบ: Meteor, Shooting Star, Mega Meteor, EXO Moon, Alternate Moon, EXO Planet, Alternate Planet, Hell Planet และบอส Black Hole (มาทุก 10 เวฟ)
- อัปเกรดได้ถึง Lv 5 ขายได้ เร่งเกมได้ x1/x2/x3 มีสมุดดวงดาวและหน้าวิธีเล่น

## วิธีได้ไฟล์ APK (ลิงก์ดาวน์โหลด)
### ทางที่ 1: ให้ GitHub build ให้ (ไม่ต้องลงโปรแกรมอะไร)
1. สร้าง repository ใหม่บน GitHub แล้วอัปโหลดไฟล์ทั้งหมดในโฟลเดอร์นี้ (รวมโฟลเดอร์ `.github`)
2. GitHub Actions จะ build ให้เองอัตโนมัติ ใช้เวลาประมาณ 3–5 นาที
3. เข้าไปที่หน้า **Releases** ของ repo จะมีไฟล์ `StellaTD.apk` ให้ดาวน์โหลด
   ลิงก์ถาวรคือ `https://github.com/<ชื่อคุณ>/<ชื่อ repo>/releases/latest/download/StellaTD.apk`
4. เปิดลิงก์บนมือถือ Android แล้วกดติดตั้ง (ถ้าเครื่องถาม ให้อนุญาต "ติดตั้งแอปจากแหล่งที่ไม่รู้จัก")

### ทางที่ 2: build เองด้วย Android Studio
เปิดโฟลเดอร์นี้ใน Android Studio แล้วกด Run หรือใช้คำสั่ง `./gradlew assembleRelease`
ไฟล์จะอยู่ที่ `app/build/outputs/apk/release/app-release.apk`

## โครงสร้างโค้ด
- `Defs.java` ค่าของดวงดาว enchant effect ศัตรู และแผนที่ทุกด่าน (ปรับความยากหรือความแรงได้ที่นี่)
- `Game.java` ระบบเกมทั้งหมด
- `Art.java` วาดภาพการ์ตูนทุกตัวด้วยโค้ด (ไม่ต้องใช้ไฟล์รูป)
- `GameView.java` หน้าจอ ปุ่ม และการแตะ
