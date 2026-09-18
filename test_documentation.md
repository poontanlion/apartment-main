# Test Documentation (เอกสารอธิบายการทำงานของ Unit Tests)

เอกสารนี้อธิบายการทำงานของ Unit Tests ที่สร้างขึ้นมาใหม่ เพื่อครอบคลุมเส้นทางของ API (API Endpoints) แต่ละเส้น โดยแบ่งเป็นกรณีสำเร็จ (Success) และไม่สำเร็จ (Failure) ตามที่ระบุใน Requirement

## 1. การทำงานของ Test ทำงานตรงไหนและอย่างไร?
Unit Test ของ Spring Boot ในโปรเจกต์นี้ใช้ `@WebMvcTest` ร่วมกับ `MockMvc` ในการจำลองการส่ง HTTP Request (GET, POST, PUT, DELETE) เข้ามาที่ Controller โดยไม่จำเป็นต้องสตาร์ท Database จริง (ใช้ `@MockBean` สร้างของจำลอง (Mock) ของ Repository และ Service ต่างๆ) 

## 2. รายละเอียดแต่ละคลาสที่ใช้ทดสอบ (Test Cases Detail)

### 2.1 RoomControllerTest
ทดสอบการทำงานของ `RoomController` สำหรับจัดการข้อมูลห้องพัก
- **`getAllRooms_Success()`**: ทดสอบเส้นทาง `GET /api/rooms` โดยจำลองว่ามีข้อมูลห้องพักในฐานข้อมูล ผลลัพธ์คาดหวังคือ HTTP Status `200 OK` และได้ข้อมูลกลับมาถูกต้อง
- **`getRoomById_Success()`**: ทดสอบเส้นทาง `GET /api/rooms/{id}` แบบมี ID จริง ผลลัพธ์ต้องได้ HTTP `200 OK`
- **`getRoomById_NotFound()`**: ทดสอบเส้นทาง `GET /api/rooms/{id}` กรณีใส่ ID ที่ไม่มีอยู่จริง ผลลัพธ์คาดหวังคือ HTTP Status `404 Not Found` (Failure Case)
- **`createRoom_Success()`**: ทดสอบเส้นทาง `POST /api/rooms` ส่ง JSON สร้างห้องใหม่เข้าไป คาดหวัง `200 OK`
- **`updateRoom_Success()` / `updateRoom_NotFound()`**: ทดสอบการแก้ข้อมูล `PUT /api/rooms/{id}` แบบเจอห้อง (200 OK) และแบบไม่เจอห้อง (404 Not Found)
- **`deleteRoom_Success()` / `deleteRoom_NotFound()`**: ทดสอบการลบห้องพัก (200 OK และ 404 Not Found)

### 2.2 BookingControllerTest
ทดสอบการทำงานของ `BookingController` (ระบบจองห้องพัก) ซึ่งมี Business Logic พิเศษซ่อนอยู่
- **`getAllBookings_Success()`**: เช็คเส้น `GET /api/bookings` ว่าคืนค่าข้อมูลการจองทั้งหมด (200 OK)
- **`createBooking_Success()`**: เช็ค `POST /api/bookings` กรณีสร้างการจองใหม่เมื่อห้องนั้น `Available` คาดหวังจะได้ข้อมูลการจองบันทึกสำเร็จ (200 OK)
- **`createBooking_RoomOccupied_Conflict()`**: (Failure Case ที่สำคัญ) ทดสอบส่ง `POST /api/bookings` ไปยังห้องที่มีคนเช่าอยู่แล้ว (`Occupied`) ระบบต้องป้องกันและตอบกลับด้วย HTTP Status `409 Conflict` เพื่อบอกว่าห้องไม่ว่าง
- **`cancelBooking_Success()`**: ทดสอบ `PUT /api/bookings/{id}/cancel` เพื่อยกเลิกการจอง 
- **`cancelBooking_NotFound()`**: ลองยกเลิกการจองด้วย ID มั่ว คาดหวังผล 404 Not Found

> **หมายเหตุ:** สำหรับ Controller อื่นๆ เช่น `UtilityBillController` และ `MaintenanceController` ก็ใช้โครงสร้างการทดสอบคล้ายคลึงกัน (ทดสอบ Success/Failure ของ CRUD operations) 

## 3. สรุปความครอบคลุม (Coverage)
จากการเขียน Unit Test แบบแตก 1 เส้นทาง เป็น 2 เคส (Success/Failure) ช่วยให้:
1. การันตีว่าเวลาหาข้อมูลไม่เจอ ระบบไม่ล่ม (พ่น 500) แต่จะพ่น 404 ออกมาอย่างถูกต้องตามหลัก RESTful
2. การันตี Business Logic ขัดแย้ง เช่น การจองห้องซ้อน จะถูกตีกลับเป็น Error 409 เสมอ 
