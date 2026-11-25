# Stella TD

เกม Tower Defense บนมือถือ (Android แท้ ไม่ใช่เว็บ) ป้องกันฐานคริสตัลจากอุกกาบาตและหลุมดำด้วยดวงดาว

## มีอะไรในเกม (v2.0)
- **Lobby**: ข้อมูลผู้เล่น, ตั้งค่า (เปลี่ยนภาษาไทย/English), ระบบสุริยะตรงกลาง, แถบเมนู Shop | Planet | Play | Banner | Enchant
- **Planet**: ดูสถานะ/ความหายาก, อัปเกรด Lv (ใช้การ์ดซ้ำ + Star), Evolve (เร็ว ๆ นี้), จัด Deck ได้ 4 ดวง, ใช้ Effect Ticket
- **Play**: Story 4 ด่าน (Challenge / Raid เร็ว ๆ นี้)
- **Banner**: Regular สุ่ม 1 (100 Star) / สุ่ม 10 (900 Star)
- **Enchant**: สุ่ม Enchant ด้วย Millite, สุ่มอัตโนมัติจนกว่าจะได้ตัวที่ต้องการ, Index บอกโอกาส
- **Shop**: Effect Ticket (500 Star) ส่วนอื่นเร็ว ๆ นี้
- Star ได้จากการเล่นด่าน, Millite ได้จากมินิบอส (2) และบอส (3)
- ในด่านใช้ SP วางดวงดาวจาก Deck ของคุณ

## ฟอนต์ประจำเกม
ใช้ฟอนต์ **Kanit** (สัญญาอนุญาต SIL Open Font License ใช้ในเกมได้ฟรี) ระบบ build บน GitHub จะดาวน์โหลดให้อัตโนมัติ
ถ้า build เองในคอม ให้โหลด Kanit-SemiBold.ttf และ Kanit-BlackItalic.ttf มาใส่ใน `app/src/main/assets/fonts/`

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
