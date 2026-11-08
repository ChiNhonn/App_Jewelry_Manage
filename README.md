# JewelryManagement – Ứng dụng Android quản lý cửa hàng trang sức (Kotlin, XML, SQLite)

Ứng dụng Android Native phục vụ môn **Lập trình ứng dụng Android**. Toàn bộ dữ liệu nằm trong SQLite trên máy.
Chức năng quên mật khẩu được viết hoàn toàn bằng Kotlin trong ứng dụng: sinh OTP, gửi Gmail qua SMTP, xác minh và
đổi mật khẩu – **không có backend, không Spring Boot, không Firebase**.

---

## 1. Công nghệ

- Kotlin, XML Layout, ViewBinding, Material Components, RecyclerView.
- SQLite qua `SQLiteOpenHelper` (không dùng Room, Firebase, MySQL, Compose).
- `SessionManager` (SharedPreferences) lưu phiên đăng nhập; `CompareManager` lưu danh sách so sánh theo từng tài khoản.
- `PdfDocument` để tạo PDF; `XlsxExporter` tự viết file `.xlsx` chuẩn Office Open XML (không cần thư viện ngoài).
- Storage Access Framework (`CreateDocument`) để người dùng tự chọn nơi lưu báo cáo.
- `NotificationChannel` + quyền `POST_NOTIFICATIONS` (Android 13+) cho thông báo cục bộ.
- Gửi OTP: thư viện `com.sun.mail:android-mail` (JavaMail cho Android) tới `smtp.gmail.com:587` + STARTTLS,
  chạy trong Kotlin Coroutines (`lifecycleScope` + `Dispatchers.IO`) để không treo giao diện.

## 2. Tài khoản mẫu

| Vai trò | Tên đăng nhập | Mật khẩu | Email |
| :--- | :--- | :--- | :--- |
| Admin | `admin` | `admin123` | `duongtrinhan00@gmail.com` |
| Khách hàng | `customer1` | `customer123` | `customer1@gmail.com` |

Email của Admin mẫu là hộp thư chủ dự án dùng để thử OTP thật. Vai trò Admin không phụ thuộc vào email; đăng ký mới luôn là Khách hàng.
`customer1@gmail.com` không phải hộp thư thật: muốn thử OTP với khách hàng, hãy đăng ký tài khoản mới bằng một Gmail bạn mở được.

---

## 3. Chạy ứng dụng Android

1. Mở thư mục dự án bằng Android Studio, đợi Gradle sync.
2. Chạy cấu hình `app` trên Emulator hoặc điện thoại (minSdk 26).
3. Lần đầu mở, CSDL được tạo kèm dữ liệu mẫu. Nếu máy đã có CSDL phiên bản cũ, ứng dụng **nâng cấp không phá dữ liệu**
   (xem mục 6) – tài khoản và đơn hàng cũ được giữ nguyên.

Lệnh dòng lệnh đã dùng để kiểm tra:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

---

## 4. Cấu hình Gmail gửi OTP

### 4.1. Tạo Gmail App Password

Dùng một **Gmail thử nghiệm riêng** cho đồ án, không dùng Gmail cá nhân chính.

1. Vào <https://myaccount.google.com/security>, bật **Xác minh 2 bước**.
2. Mở **Mật khẩu ứng dụng (App passwords)**, tạo mật khẩu tên `JewelryManagement`, sao chép chuỗi 16 ký tự.

### 4.2. Khai báo cho ứng dụng

Sao chép file mẫu `secrets.properties.example` (thư mục gốc dự án) thành `secrets.properties` rồi điền giá trị thật:

```properties
MAIL_USERNAME=your-demo-sender@gmail.com
MAIL_APP_PASSWORD=xxxx xxxx xxxx xxxx
```

Sau đó **Sync Gradle** và chạy lại app. Gradle đọc hai khóa này theo thứ tự: `secrets.properties` → `local.properties`
→ biến môi trường cùng tên, rồi đưa vào `BuildConfig.MAIL_USERNAME` / `BuildConfig.MAIL_APP_PASSWORD`.
Nếu chưa cấu hình, app vẫn chạy bình thường; màn hình quên mật khẩu báo "Chưa cấu hình Gmail gửi OTP".

### 4.3. Nguyên tắc bảo mật

- Không ghi Gmail hay App Password vào lớp Kotlin, `strings.xml`, `AndroidManifest.xml`, README hay Logcat
  (log chỉ ghi tên loại lỗi, không ghi email, OTP hay mật khẩu).
- `secrets.properties`, `local.properties`, `.env` nằm trong `.gitignore`; chỉ `secrets.properties.example` (giá trị giả) được commit.
- OTP **không bao giờ** được trả về giao diện; SQLite chỉ lưu bản băm SHA-256 có salt của OTP và của token đổi mật khẩu.
- Mật khẩu người dùng lưu bằng PBKDF2-HMAC-SHA256 (salt ngẫu nhiên, 60.000 vòng). Mật khẩu kiểu cũ (SHA-256) vẫn đăng nhập được
  và được băm lại tự động sau lần đăng nhập đầu tiên.

> **Lưu ý:** vì không có máy chủ, App Password được đóng gói vào APK lúc build và **có thể bị trích xuất** từ file APK.
> Chỉ dùng Gmail thử nghiệm cho mục đích demo môn học. Nếu App Password từng bị lộ (dán vào chat, tài liệu, commit),
> hãy thu hồi và tạo App Password mới.

### 4.4. Luồng và quy tắc OTP

1. **Quên mật khẩu?** → nhập email → app tìm tài khoản trong SQLite (Admin hoặc Khách hàng). Email chưa đăng ký sẽ được báo rõ.
2. App sinh OTP 6 số bằng `SecureRandom`, gửi Gmail với tiêu đề "Mã xác minh khôi phục mật khẩu JewelryManagement",
   chỉ lưu bản băm vào bảng `otp_sessions`.
3. Màn hình xác minh: đếm ngược thời gian hiệu lực, nút gửi lại có đếm ngược. Đúng mã → app cấp một token ngẫu nhiên dùng một lần.
4. Màn hình đặt lại mật khẩu chỉ mở được khi có token này; cập nhật mật khẩu và hủy mã trong **một transaction**,
   sau đó báo "Đổi mật khẩu thành công" và quay về Đăng nhập. Hồ sơ, giỏ hàng, yêu thích, đơn hàng giữ nguyên.

| Quy tắc | Giá trị |
| :--- | :--- |
| Độ dài / hiệu lực | 6 chữ số, 5 phút, dùng 1 lần |
| Nhập sai tối đa | 5 lần, sau đó mã bị hủy |
| Gửi lại | sau 60 giây; tối đa 5 mã/email/giờ; mã mới thay thế mã cũ |
| Token đổi mật khẩu | 10 phút, chỉ dùng được 1 lần |
| Mật khẩu mới | không rỗng, tối thiểu 6 ký tự, khớp ô xác nhận |

Không có mạng, Gmail từ chối đăng nhập, gửi thất bại, mã sai/hết hạn đều hiển thị thông báo tiếng Việt; mọi thao tác
mạng và CSDL chạy trên `Dispatchers.IO` nên giao diện không bị treo.

---

## 5. Quy tắc giá, khuyến mãi và coupon

1. `products.price` luôn là **giá gốc**, không bao giờ bị ghi đè bởi khuyến mãi.
2. Mỗi sản phẩm chỉ nhận **một** khuyến mãi đang diễn ra có mức giảm lớn nhất (không cộng dồn nhiều sự kiện).
   Trạng thái sự kiện (Sắp diễn ra / Đang diễn ra / Đã kết thúc / Tạm ngừng) được tính theo thời gian hiện tại, không lưu cứng.
3. Coupon **không cộng dồn với sự kiện**: coupon chỉ giảm trên phần tiền của các sản phẩm *không* có khuyến mãi;
   điều kiện "đơn tối thiểu" cũng tính trên phần này. Nếu mọi sản phẩm đều đang khuyến mãi thì coupon bị từ chối kèm lý do.
4. Thứ tự áp dụng: giá gốc → giảm sự kiện (theo từng sản phẩm) → giảm coupon (trên phần hợp lệ, có mức tối đa) → tổng thanh toán.
   Mọi số tiền được làm tròn tới đồng.
5. Khi đặt hàng, trong **một transaction SQLite**: đọc lại giỏ, tính lại giá, kiểm tra tồn kho, so với tổng khách đã thấy
   (lệch giá → hủy và hiển thị lại), tăng `used_count` của coupon bằng câu lệnh có điều kiện (không vượt giới hạn),
   lưu đơn kèm **snapshot** (giá gốc, giảm khuyến mãi, tên khuyến mãi, mã coupon, tiền giảm, tổng cuối), trừ kho, xóa giỏ.
6. Hủy đơn: hoàn kho và trả lại một lượt dùng coupon. "Đã giao hàng" và "Đã hủy" là trạng thái cuối.
7. Doanh thu (Dashboard, Thống kê, Báo cáo) chỉ tính đơn **Đã giao hàng**.

## 6. Cơ sở dữ liệu và nâng cấp

- File `jewelry_management.db`, phiên bản **4**, `PRAGMA foreign_keys = ON`.
- Bảng: `users`, `categories`, `products`, `cart_items`, `orders`, `order_details`, `promotions`, `promotion_targets`,
  `coupons`, `notifications`, `product_reviews`, `favorites`, `otp_sessions` (yêu cầu khôi phục mật khẩu, mỗi email một dòng).
- Ràng buộc duy nhất: giỏ hàng (user, sản phẩm), yêu thích (user, sản phẩm), đánh giá (đơn, sản phẩm, user),
  thông báo theo sự kiện (đơn, user, loại) để không tạo trùng; chỉ mục cho các truy vấn theo danh mục, user, trạng thái/ngày.
- `onUpgrade` chỉ **thêm** bảng/cột còn thiếu (`ALTER TABLE ... ADD COLUMN`, `CREATE TABLE IF NOT EXISTS`), không xóa dữ liệu.
- Mọi truy vấn dùng tham số `?`; Activity/Adapter không chứa SQL (đi qua DAO / Repository).

## 7. Thông báo

Thông báo là **thông báo cục bộ** trên chính thiết bị, không phải push giữa hai điện thoại. Khi Admin chuyển đơn sang
"Đã giao hàng", ứng dụng tạo đúng một bản ghi thông báo cho `user_id` chủ đơn (lưu lại lần nữa không tạo trùng) và hiển thị
thông báo Android trên thiết bị đang chạy (nếu đã cấp quyền). Khách hàng thấy thông báo trong mục **Thông báo** khi đăng nhập
trên thiết bị đó.

---

## 8. Kịch bản kiểm thử

### 8.1. Khuyến mãi và coupon
1. Admin → **Khuyến mãi** → tạo sự kiện giảm 15% cho danh mục Nhẫn, thời gian bao gồm hiện tại.
2. Khách hàng: trang chủ/chi tiết hiển thị giá gốc gạch ngang, nhãn `-15%`, giá sau giảm; giỏ hàng dùng đúng giá này.
3. Admin → **Coupon** → tạo `SALE10` (10%, đơn tối thiểu 1.000.000 ₫, tối đa 500.000 ₫, 5 lượt).
4. Checkout chỉ có sản phẩm đang khuyến mãi + nhập `SALE10` → báo "không cộng dồn ưu đãi".
5. Thêm sản phẩm không khuyến mãi → áp dụng lại → hiển thị Tổng tiền hàng, Giảm giá sự kiện, Giảm coupon, Tổng thanh toán.
6. Thử mã sai / hết hạn / chưa bắt đầu / hết lượt / chưa đủ tối thiểu → mỗi trường hợp có thông báo tiếng Việt riêng.
7. Đặt hàng → `used_count` tăng 1; hủy đơn → giảm 1 và kho được hoàn.

### 8.2. Đánh giá và thông báo
1. Admin → Quản lý đơn → đơn của `customer1` → chuyển lần lượt tới **Đã giao hàng**.
2. Thông báo Android xuất hiện; lưu lại lần nữa không tạo thông báo mới.
3. Đăng nhập `customer1` → biểu tượng chuông có số chưa đọc → mở thông báo → **Đánh giá ngay** / **Để sau**.
4. Không chọn sao → không gửi được; chọn sao + nhận xét → "Cảm ơn bạn đã dành thời gian đánh giá sản phẩm của JewelryManagement!".
5. Đánh giá lại cùng sản phẩm trong cùng đơn → bị chặn. Trang sản phẩm cập nhật điểm trung bình, số lượt, phân bố sao.
6. Admin → **Đánh giá** → ẩn/hiện nhận xét (không sửa được số sao).

### 8.3. Yêu thích và so sánh
1. Bấm tim trên thẻ/chi tiết sản phẩm → mở **Yêu thích**; đăng xuất/đăng nhập lại vẫn còn.
2. "Chuyển vào giỏ" chỉ thành công khi còn hàng; sản phẩm ngừng kinh doanh được đánh dấu rõ.
3. Thêm tối đa 3 sản phẩm vào **So sánh** → bảng cột sản phẩm × hàng thuộc tính; thiếu dữ liệu hiện "Chưa có thông tin";
   khác danh mục hiện cảnh báo.

### 8.4. Báo cáo PDF / Excel
1. Admin → **Xuất báo cáo** → chọn loại, khoảng ngày (ngày bắt đầu không được sau ngày kết thúc), trạng thái đơn, định dạng.
2. Chọn nơi lưu qua SAF → sau khi lưu có **Mở tệp** / **Chia sẻ**. Không có ứng dụng mở được → thông báo, không crash.
3. PDF: tiếng Việt, VNĐ, bảng tự xuống dòng, tiêu đề bảng lặp lại khi sang trang, "Trang x/y" ở mọi trang.
4. Excel: sheet Tổng quan + một sheet mỗi bảng, tiền là số (định dạng `#,##0 ₫`), ngày là ngày Excel, có lọc và cố định tiêu đề.
5. Khoảng ngày không có dữ liệu → hỏi "Vẫn xuất?"; hủy hộp thoại lưu → không tạo file.

### 8.5. Quên mật khẩu (OTP Gmail)
1. Tạo `secrets.properties` (mục 4.2), Sync Gradle, chạy app → Đăng nhập → **Quên mật khẩu?**.
2. **Admin:** nhập `duongtrinhan00@gmail.com` → nhận mã trong Gmail (kiểm tra cả Spam) → nhập mã → đặt mật khẩu mới
   → đăng nhập `admin` bằng mật khẩu mới (mật khẩu cũ bị từ chối).
3. **Khách hàng:** đăng ký tài khoản bằng một Gmail thật bạn mở được, lặp lại bước 2; giỏ hàng/yêu thích/đơn hàng vẫn còn.
4. Nhập sai mã → báo số lần còn lại; sai 5 lần → phải yêu cầu mã mới; đợi quá 5 phút → báo mã hết hạn;
   bấm gửi lại trước 60 giây → nút bị khóa có đếm ngược.
5. Tắt Wi-Fi/dữ liệu di động rồi gửi mã → báo không có kết nối mạng, không crash.

---

## 9. Kiểm thử tự động

`app/src/test`: `PricingRulesTest` (trạng thái khuyến mãi, chọn khuyến mãi tốt nhất, làm tròn, giới hạn coupon, lý do từ chối,
định dạng ngày), `XlsxExporterTest` (file `.xlsx` hợp lệ, mọi phần XML phân tích được, tiền là số, ngày là số serial, ký tự đặc biệt được escape)
và `PasswordAndOtpUtilsTest` (PBKDF2 có salt, mật khẩu SHA-256 cũ, OTP 6 số, băm OTP, định dạng đếm ngược).

`app/src/androidTest` (chạy trên Emulator, **xóa và nạp lại dữ liệu mẫu** trước/sau khi chạy):
- `OrderFlowInstrumentedTest`: đăng nhập tài khoản mẫu; khuyến mãi + coupon + đặt hàng trong transaction; sai tổng tiền thì không ghi;
  hết lượt coupon; hủy đơn hoàn kho; giao hàng tạo đúng 1 thông báo; quy tắc đánh giá; tạo báo cáo PDF/XLSX.
- `ScreenSmokeInstrumentedTest`: mở lần lượt các màn hình Admin, Khách hàng, đăng nhập/OTP và kiểm tra không bị crash.
- `PasswordResetInstrumentedTest`: luồng quên mật khẩu cho Admin và Khách hàng với bộ gửi mail giả (không gửi Gmail thật):
  sai mã, đúng mã, mã dùng lại, token giả/dùng lại/hết hạn, OTP hết hạn, sai 5 lần, gửi lại sau 60 giây, giỏ hàng được giữ nguyên.

Chạy: `.\gradlew.bat :app:connectedDebugAndroidTest` (cần mạng lần đầu để Gradle tải test runner).

## 10. Cấu trúc mã nguồn chính

```text
app/src/main/java/com/example/jewelrymanagement/
├── activities/      Màn hình (Admin, Khách hàng, OTP, báo cáo, so sánh, thông báo...)
├── adapters/        RecyclerView adapters
├── database/        DatabaseHelper + DAO (SQL, transaction), OtpSessionDao
├── repositories/    PricingRepository (giá/coupon), ReportRepository (dữ liệu báo cáo),
│                    PasswordResetRepository (luồng quên mật khẩu)
├── models/          Data class (gồm OtpSession)
└── utils/           DateUtils, CurrencyUtils, SessionManager, CompareManager, NotificationHelper,
                     PdfReportWriter, XlsxExporter, PasswordUtils, OtpUtils, GmailOtpSender
secrets.properties.example   Mẫu cấu hình Gmail gửi OTP (giá trị giả)
```
