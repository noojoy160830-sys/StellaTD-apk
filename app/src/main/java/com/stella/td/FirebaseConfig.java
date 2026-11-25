package com.stella.td;

/**
 * ===== ใส่ค่าจาก Firebase ของคุณตรงนี้ (Put your Firebase values here) =====
 * 1. API_KEY: Firebase console -> Project settings (เฟือง) -> General -> "Web API Key"
 * 2. DB_URL : Firebase console -> Realtime Database -> ลิงก์ด้านบน เช่น
 *            https://stella-td-default-rtdb.asia-southeast1.firebasedatabase.app
 *            (ไม่ต้องมี / ปิดท้าย)
 */
final class FirebaseConfig {
    private FirebaseConfig() {}

    static final String API_KEY = "PASTE_YOUR_WEB_API_KEY_HERE";
    static final String DB_URL = "PASTE_YOUR_DATABASE_URL_HERE";
}
