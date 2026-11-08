package com.example.jewelrymanagement.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.jewelrymanagement.utils.DateUtils
import com.example.jewelrymanagement.utils.PasswordUtils
import java.util.Calendar

/**
 * Single shared SQLiteOpenHelper. Use [getInstance] so every DAO works on the same
 * connection, which keeps transactions (order placement, coupon usage) serialized.
 */
class DatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "jewelry_management.db"

        /**
         * v1: base schema
         * v2: promotions, coupons, notifications, reviews, favorites, order discount columns
         * v3: promotion_targets relation, product attributes, discount snapshots per order/line,
         *     notification de-duplication, indexes
         */
        const val DATABASE_VERSION = 6

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context).also { instance = it }
            }

        // Order statuses (stored values)
        const val STATUS_PENDING = "Chờ xác nhận"
        const val STATUS_CONFIRMED = "Đã xác nhận"
        const val STATUS_SHIPPING = "Đang giao hàng"
        const val STATUS_DELIVERED = "Đã giao hàng"
        const val STATUS_CANCELLED = "Đã hủy"
        val ORDER_STATUSES = listOf(STATUS_PENDING, STATUS_CONFIRMED, STATUS_SHIPPING, STATUS_DELIVERED, STATUS_CANCELLED)

        // Table Users
        const val TABLE_USERS = "users"
        const val COL_USER_ID = "id"
        const val COL_USER_USERNAME = "username"
        const val COL_USER_FULL_NAME = "full_name"
        const val COL_USER_EMAIL = "email"
        const val COL_USER_PHONE = "phone"
        const val COL_USER_PASSWORD_HASH = "password_hash"
        const val COL_USER_ROLE = "role" // "ADMIN" or "CUSTOMER"
        const val COL_USER_ADDRESS = "address"
        const val COL_USER_IS_LOCKED = "is_locked" // 1 = admin locked this customer out

        // Table Categories
        const val TABLE_CATEGORIES = "categories"
        const val COL_CAT_ID = "id"
        const val COL_CAT_NAME = "name"
        const val COL_CAT_DESCRIPTION = "description"

        // Table Products
        const val TABLE_PRODUCTS = "products"
        const val COL_PROD_ID = "id"
        const val COL_PROD_NAME = "name"
        const val COL_PROD_DESCRIPTION = "description"
        const val COL_PROD_PRICE = "price"
        const val COL_PROD_QUANTITY = "quantity"
        const val COL_PROD_CATEGORY_ID = "category_id"
        const val COL_PROD_MATERIAL = "material"
        const val COL_PROD_WEIGHT = "weight"
        const val COL_PROD_IMAGE_NAME = "image_name"
        const val COL_PROD_CREATED_AT = "created_at"
        const val COL_PROD_GEM_TYPE = "gem_type"
        const val COL_PROD_PURITY = "purity"
        const val COL_PROD_SIZE = "size"
        const val COL_PROD_WARRANTY = "warranty"

        // Table Cart Items
        const val TABLE_CART = "cart_items"
        const val COL_CART_ID = "id"
        const val COL_CART_USER_ID = "user_id"
        const val COL_CART_PROD_ID = "product_id"
        const val COL_CART_QUANTITY = "quantity"

        // Table Orders
        const val TABLE_ORDERS = "orders"
        const val COL_ORDER_ID = "id"
        const val COL_ORDER_USER_ID = "user_id"
        const val COL_ORDER_RECEIVER_NAME = "receiver_name"
        const val COL_ORDER_PHONE = "phone"
        const val COL_ORDER_SHIPPING_ADDRESS = "shipping_address"
        /** Sum of original (list) prices x quantity at order time. */
        const val COL_ORDER_TOTAL_AMOUNT = "total_amount"
        /** Total discount = promotion discount + coupon discount. */
        const val COL_ORDER_DISCOUNT_AMOUNT = "discount_amount"
        const val COL_ORDER_PROMO_DISCOUNT = "promotion_discount"
        const val COL_ORDER_COUPON_DISCOUNT = "coupon_discount"
        const val COL_ORDER_COUPON_CODE = "coupon_code"
        const val COL_ORDER_FINAL_AMOUNT = "final_amount"
        const val COL_ORDER_PAYMENT_METHOD = "payment_method"
        const val COL_ORDER_PAYMENT_STATUS = "payment_status"
        const val COL_ORDER_STATUS = "order_status"
        const val COL_ORDER_CREATED_AT = "created_at"

        // Table Order Details
        const val TABLE_ORDER_DETAILS = "order_details"
        const val COL_DETAIL_ID = "id"
        const val COL_DETAIL_ORDER_ID = "order_id"
        const val COL_DETAIL_PROD_ID = "product_id"
        const val COL_DETAIL_PROD_NAME = "product_name"
        const val COL_DETAIL_QUANTITY = "quantity"
        /** Price actually charged per unit (after promotion). */
        const val COL_DETAIL_UNIT_PRICE = "unit_price"
        const val COL_DETAIL_SUBTOTAL = "subtotal"
        const val COL_DETAIL_ORIGINAL_PRICE = "original_unit_price"
        /** Promotion discount for the whole line (per-unit discount x quantity). */
        const val COL_DETAIL_PROMO_DISCOUNT = "promotion_discount"
        const val COL_DETAIL_PROMO_NAME = "promotion_name"

        // Table Promotions (events)
        const val TABLE_PROMOTIONS = "promotions"
        const val COL_PROMO_ID = "id"
        const val COL_PROMO_CODE = "code"
        const val COL_PROMO_NAME = "name"
        const val COL_PROMO_DESCRIPTION = "description"
        const val COL_PROMO_DISCOUNT_TYPE = "discount_type"
        const val COL_PROMO_DISCOUNT_VALUE = "discount_value"
        const val COL_PROMO_START_DATE = "start_date"
        const val COL_PROMO_END_DATE = "end_date"
        /** "ALL", "CATEGORY" or "PRODUCT"; concrete targets live in promotion_targets. */
        const val COL_PROMO_TARGET_TYPE = "target_type"
        private const val COL_PROMO_LEGACY_TARGET_ID = "target_id"
        const val COL_PROMO_IS_ACTIVE = "is_active"
        const val COL_PROMO_CREATED_AT = "created_at"

        // Table Promotion Targets
        const val TABLE_PROMO_TARGETS = "promotion_targets"
        const val COL_PT_ID = "id"
        const val COL_PT_PROMO_ID = "promotion_id"
        const val COL_PT_PROD_ID = "product_id"
        const val COL_PT_CAT_ID = "category_id"

        // Table Coupons
        const val TABLE_COUPONS = "coupons"
        const val COL_COUPON_ID = "id"
        const val COL_COUPON_CODE = "code"
        const val COL_COUPON_TITLE = "title"
        const val COL_COUPON_DISCOUNT_TYPE = "discount_type"
        const val COL_COUPON_DISCOUNT_VALUE = "discount_value"
        const val COL_COUPON_MIN_ORDER = "min_order_amount"
        const val COL_COUPON_MAX_DISCOUNT = "max_discount_amount"
        const val COL_COUPON_START_DATE = "start_date"
        const val COL_COUPON_END_DATE = "end_date"
        const val COL_COUPON_USAGE_LIMIT = "usage_limit"
        const val COL_COUPON_USED_COUNT = "used_count"
        const val COL_COUPON_IS_ACTIVE = "is_active"
        const val COL_COUPON_CREATED_AT = "created_at"

        // Table Notifications
        const val TABLE_NOTIFICATIONS = "notifications"
        const val COL_NOTIF_ID = "id"
        const val COL_NOTIF_USER_ID = "user_id"
        const val COL_NOTIF_TITLE = "title"
        const val COL_NOTIF_CONTENT = "content"
        const val COL_NOTIF_TYPE = "type"
        const val COL_NOTIF_ORDER_ID = "order_id"
        const val COL_NOTIF_PROD_ID = "product_id"
        const val COL_NOTIF_ACTION = "action_type"
        const val COL_NOTIF_IS_READ = "is_read"
        const val COL_NOTIF_CREATED_AT = "created_at"

        // Table Product Reviews
        const val TABLE_REVIEWS = "product_reviews"
        const val COL_REVIEW_ID = "id"
        const val COL_REVIEW_PROD_ID = "product_id"
        const val COL_REVIEW_ORDER_ID = "order_id"
        const val COL_REVIEW_USER_ID = "user_id"
        const val COL_REVIEW_USER_NAME = "user_name"
        const val COL_REVIEW_RATING = "rating"
        const val COL_REVIEW_COMMENT = "comment"
        const val COL_REVIEW_IS_VISIBLE = "is_visible"
        const val COL_REVIEW_CREATED_AT = "created_at"

        // Table Favorites
        const val TABLE_FAVORITES = "favorites"
        const val COL_FAV_ID = "id"
        const val COL_FAV_USER_ID = "user_id"
        const val COL_FAV_PROD_ID = "product_id"
        const val COL_FAV_CREATED_AT = "created_at"

        // Password-reset OTP requests (v4). Times are epoch milliseconds.
        const val TABLE_OTP_SESSIONS = "otp_sessions"
        const val COL_OTP_ID = "id"
        const val COL_OTP_USER_ID = "user_id"
        const val COL_OTP_EMAIL = "email"
        const val COL_OTP_PURPOSE = "purpose"
        const val COL_OTP_HASH = "otp_hash"
        const val COL_OTP_SALT = "otp_salt"
        const val COL_OTP_EXPIRES_AT = "expires_at"
        const val COL_OTP_LAST_SENT_AT = "last_sent_at"
        const val COL_OTP_SEND_COUNT = "send_count"
        const val COL_OTP_SEND_WINDOW_START = "send_window_start"
        const val COL_OTP_FAILED_ATTEMPTS = "failed_attempts"
        const val COL_OTP_VERIFIED_AT = "verified_at"
        const val COL_OTP_TOKEN_HASH = "reset_token_hash"
        const val COL_OTP_USED = "used"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_USERS (
                $COL_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_USER_USERNAME TEXT NOT NULL UNIQUE,
                $COL_USER_FULL_NAME TEXT NOT NULL,
                $COL_USER_EMAIL TEXT NOT NULL UNIQUE,
                $COL_USER_PHONE TEXT,
                $COL_USER_PASSWORD_HASH TEXT NOT NULL,
                $COL_USER_ROLE TEXT NOT NULL,
                $COL_USER_ADDRESS TEXT,
                $COL_USER_IS_LOCKED INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_CATEGORIES (
                $COL_CAT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CAT_NAME TEXT NOT NULL,
                $COL_CAT_DESCRIPTION TEXT
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_PRODUCTS (
                $COL_PROD_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PROD_NAME TEXT NOT NULL,
                $COL_PROD_DESCRIPTION TEXT,
                $COL_PROD_PRICE REAL NOT NULL,
                $COL_PROD_QUANTITY INTEGER NOT NULL,
                $COL_PROD_CATEGORY_ID INTEGER NOT NULL,
                $COL_PROD_MATERIAL TEXT,
                $COL_PROD_WEIGHT TEXT,
                $COL_PROD_IMAGE_NAME TEXT,
                $COL_PROD_CREATED_AT TEXT,
                $COL_PROD_GEM_TYPE TEXT,
                $COL_PROD_PURITY TEXT,
                $COL_PROD_SIZE TEXT,
                $COL_PROD_WARRANTY TEXT,
                FOREIGN KEY ($COL_PROD_CATEGORY_ID) REFERENCES $TABLE_CATEGORIES($COL_CAT_ID) ON DELETE RESTRICT
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_CART (
                $COL_CART_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CART_USER_ID INTEGER NOT NULL,
                $COL_CART_PROD_ID INTEGER NOT NULL,
                $COL_CART_QUANTITY INTEGER NOT NULL,
                UNIQUE($COL_CART_USER_ID, $COL_CART_PROD_ID),
                FOREIGN KEY ($COL_CART_USER_ID) REFERENCES $TABLE_USERS($COL_USER_ID) ON DELETE CASCADE,
                FOREIGN KEY ($COL_CART_PROD_ID) REFERENCES $TABLE_PRODUCTS($COL_PROD_ID) ON DELETE CASCADE
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_ORDERS (
                $COL_ORDER_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ORDER_USER_ID INTEGER NOT NULL,
                $COL_ORDER_RECEIVER_NAME TEXT NOT NULL,
                $COL_ORDER_PHONE TEXT NOT NULL,
                $COL_ORDER_SHIPPING_ADDRESS TEXT NOT NULL,
                $COL_ORDER_TOTAL_AMOUNT REAL NOT NULL,
                $COL_ORDER_DISCOUNT_AMOUNT REAL NOT NULL DEFAULT 0,
                $COL_ORDER_PROMO_DISCOUNT REAL NOT NULL DEFAULT 0,
                $COL_ORDER_COUPON_DISCOUNT REAL NOT NULL DEFAULT 0,
                $COL_ORDER_COUPON_CODE TEXT NOT NULL DEFAULT '',
                $COL_ORDER_FINAL_AMOUNT REAL NOT NULL DEFAULT 0,
                $COL_ORDER_PAYMENT_METHOD TEXT NOT NULL,
                $COL_ORDER_PAYMENT_STATUS TEXT NOT NULL,
                $COL_ORDER_STATUS TEXT NOT NULL,
                $COL_ORDER_CREATED_AT TEXT NOT NULL,
                FOREIGN KEY ($COL_ORDER_USER_ID) REFERENCES $TABLE_USERS($COL_USER_ID)
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_ORDER_DETAILS (
                $COL_DETAIL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DETAIL_ORDER_ID INTEGER NOT NULL,
                $COL_DETAIL_PROD_ID INTEGER,
                $COL_DETAIL_PROD_NAME TEXT NOT NULL,
                $COL_DETAIL_QUANTITY INTEGER NOT NULL,
                $COL_DETAIL_UNIT_PRICE REAL NOT NULL,
                $COL_DETAIL_SUBTOTAL REAL NOT NULL,
                $COL_DETAIL_ORIGINAL_PRICE REAL,
                $COL_DETAIL_PROMO_DISCOUNT REAL NOT NULL DEFAULT 0,
                $COL_DETAIL_PROMO_NAME TEXT,
                FOREIGN KEY ($COL_DETAIL_ORDER_ID) REFERENCES $TABLE_ORDERS($COL_ORDER_ID) ON DELETE CASCADE
            );
            """.trimIndent()
        )

        createPromotionsTable(db)
        createPromotionTargetsTable(db)
        createCouponsTable(db)
        createNotificationsTable(db)
        createReviewsTable(db)
        createFavoritesTable(db)
        createOtpSessionsTable(db)
        createIndexes(db)

        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Every step only adds structures or back-fills new columns; existing rows are preserved.
        if (oldVersion < 2) {
            addColumnIfMissing(db, TABLE_ORDERS, COL_ORDER_DISCOUNT_AMOUNT, "REAL NOT NULL DEFAULT 0")
            addColumnIfMissing(db, TABLE_ORDERS, COL_ORDER_COUPON_CODE, "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, TABLE_ORDERS, COL_ORDER_FINAL_AMOUNT, "REAL NOT NULL DEFAULT 0")
            db.execSQL("UPDATE $TABLE_ORDERS SET $COL_ORDER_FINAL_AMOUNT = $COL_ORDER_TOTAL_AMOUNT WHERE $COL_ORDER_FINAL_AMOUNT = 0")

            createPromotionsTable(db)
            createCouponsTable(db)
            createNotificationsTable(db)
            createReviewsTable(db)
            createFavoritesTable(db)

            // Admin account email the owner can access for real OTP testing (role unchanged).
            val cv = ContentValues().apply { put(COL_USER_EMAIL, "duongtrinhan00@gmail.com") }
            db.updateWithOnConflict(TABLE_USERS, cv, "$COL_USER_USERNAME = ? AND $COL_USER_ROLE = 'ADMIN'", arrayOf("admin"), SQLiteDatabase.CONFLICT_IGNORE)
        }

        if (oldVersion < 3) {
            addColumnIfMissing(db, TABLE_PRODUCTS, COL_PROD_GEM_TYPE, "TEXT")
            addColumnIfMissing(db, TABLE_PRODUCTS, COL_PROD_PURITY, "TEXT")
            addColumnIfMissing(db, TABLE_PRODUCTS, COL_PROD_SIZE, "TEXT")
            addColumnIfMissing(db, TABLE_PRODUCTS, COL_PROD_WARRANTY, "TEXT")

            addColumnIfMissing(db, TABLE_ORDERS, COL_ORDER_PROMO_DISCOUNT, "REAL NOT NULL DEFAULT 0")
            addColumnIfMissing(db, TABLE_ORDERS, COL_ORDER_COUPON_DISCOUNT, "REAL NOT NULL DEFAULT 0")
            // Before v3 the only discount type stored on orders was the coupon discount.
            db.execSQL(
                "UPDATE $TABLE_ORDERS SET $COL_ORDER_COUPON_DISCOUNT = $COL_ORDER_DISCOUNT_AMOUNT " +
                    "WHERE $COL_ORDER_COUPON_DISCOUNT = 0 AND $COL_ORDER_DISCOUNT_AMOUNT > 0"
            )

            addColumnIfMissing(db, TABLE_ORDER_DETAILS, COL_DETAIL_ORIGINAL_PRICE, "REAL")
            addColumnIfMissing(db, TABLE_ORDER_DETAILS, COL_DETAIL_PROMO_DISCOUNT, "REAL NOT NULL DEFAULT 0")
            addColumnIfMissing(db, TABLE_ORDER_DETAILS, COL_DETAIL_PROMO_NAME, "TEXT")
            db.execSQL("UPDATE $TABLE_ORDER_DETAILS SET $COL_DETAIL_ORIGINAL_PRICE = $COL_DETAIL_UNIT_PRICE WHERE $COL_DETAIL_ORIGINAL_PRICE IS NULL")

            addColumnIfMissing(db, TABLE_NOTIFICATIONS, COL_NOTIF_ACTION, "TEXT")
            // Remove duplicate event notifications so the unique index can be created.
            db.execSQL(
                """
                DELETE FROM $TABLE_NOTIFICATIONS
                WHERE $COL_NOTIF_ORDER_ID IS NOT NULL
                  AND $COL_NOTIF_ID NOT IN (
                    SELECT MIN($COL_NOTIF_ID) FROM $TABLE_NOTIFICATIONS
                    WHERE $COL_NOTIF_ORDER_ID IS NOT NULL
                    GROUP BY $COL_NOTIF_USER_ID, $COL_NOTIF_TYPE, $COL_NOTIF_ORDER_ID
                  )
                """.trimIndent()
            )

            createPromotionTargetsTable(db)
            if (hasColumn(db, TABLE_PROMOTIONS, COL_PROMO_LEGACY_TARGET_ID)) {
                db.execSQL(
                    """
                    INSERT INTO $TABLE_PROMO_TARGETS ($COL_PT_PROMO_ID, $COL_PT_CAT_ID)
                    SELECT p.$COL_PROMO_ID, p.$COL_PROMO_LEGACY_TARGET_ID FROM $TABLE_PROMOTIONS p
                    WHERE p.$COL_PROMO_TARGET_TYPE = 'CATEGORY'
                      AND EXISTS (SELECT 1 FROM $TABLE_CATEGORIES c WHERE c.$COL_CAT_ID = p.$COL_PROMO_LEGACY_TARGET_ID)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO $TABLE_PROMO_TARGETS ($COL_PT_PROMO_ID, $COL_PT_PROD_ID)
                    SELECT p.$COL_PROMO_ID, p.$COL_PROMO_LEGACY_TARGET_ID FROM $TABLE_PROMOTIONS p
                    WHERE p.$COL_PROMO_TARGET_TYPE = 'PRODUCT'
                      AND EXISTS (SELECT 1 FROM $TABLE_PRODUCTS pr WHERE pr.$COL_PROD_ID = p.$COL_PROMO_LEGACY_TARGET_ID)
                    """.trimIndent()
                )
            }

            createIndexes(db)
        }

        if (oldVersion < 4) {
            createOtpSessionsTable(db)
        }

        if (oldVersion < 5) {
            migrateProductImages(db)
        }

        if (oldVersion < 6) {
            addColumnIfMissing(db, TABLE_USERS, COL_USER_IS_LOCKED, "INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun migrateProductImages(db: SQLiteDatabase) {
        val seedImages = mapOf(
            "Nhẫn Kim Cương Solitaire Hoàng Gia 18K" to "jw_ring_solitaire",
            "Nhẫn Ruby Huyết Bồ Câu Đính Kim Cương" to "jw_ring_ruby",
            "Dây Chuyền Vàng Ý Khắc Hoa Văn Cung Đình" to "jw_necklace_italy",
            "Mặt Dây Chuyền Trái Tim Sapphire Xanh Biển" to "jw_pendant_sapphire",
            "Lắc Tay Bạc Ý 925 Đính Đá Tinh Khôi" to "jw_bracelet_silver",
            "Vòng Tay Bản Liền Vàng 18K Chạm Rồng Phượng" to "jw_bangle_gold",
            "Bông Tai Ngọc Trai Akoya Ánh Hồng" to "jw_earrings_pearl",
            "Bông Tai Nụ Kim Cương 4 Chấu Cổ Điển" to "jw_earrings_stud",
            "Kiềng Cưới Vàng 24K Hoa Mai May Mắn" to "jw_kieng_gold",
            "Dây Chuyền Nam Vàng 18K Mắt Xích Cuba" to "jw_chain_men",
            "Nhẫn Cặp Bạc Ý 925 Khắc Tên Vĩnh Cửu" to "jw_rings_couple",
            "Lắc Chân Bạc Nữ Chuông Nhỏ Dễ Thương" to "jw_anklet_silver"
        )
        seedImages.forEach { (name, image) ->
            db.update(TABLE_PRODUCTS, ContentValues().apply { put(COL_PROD_IMAGE_NAME, image) }, "$COL_PROD_NAME = ?", arrayOf(name))
        }
        // The vector drawables these names pointed to no longer exist.
        val legacyImages = mapOf(
            "img_ring_diamond" to "jw_ring_solitaire",
            "img_ring_ruby" to "jw_ring_ruby",
            "img_necklace_gold" to "jw_necklace_italy",
            "img_bracelet_silver" to "jw_bracelet_silver",
            "img_earrings_pearl" to "jw_earrings_pearl"
        )
        legacyImages.forEach { (old, new) ->
            db.update(TABLE_PRODUCTS, ContentValues().apply { put(COL_PROD_IMAGE_NAME, new) }, "$COL_PROD_IMAGE_NAME = ?", arrayOf(old))
        }
    }

    private fun createOtpSessionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_OTP_SESSIONS (
                $COL_OTP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_OTP_USER_ID INTEGER NOT NULL,
                $COL_OTP_EMAIL TEXT NOT NULL,
                $COL_OTP_PURPOSE TEXT NOT NULL,
                $COL_OTP_HASH TEXT NOT NULL,
                $COL_OTP_SALT TEXT NOT NULL,
                $COL_OTP_EXPIRES_AT INTEGER NOT NULL,
                $COL_OTP_LAST_SENT_AT INTEGER NOT NULL,
                $COL_OTP_SEND_COUNT INTEGER NOT NULL DEFAULT 1,
                $COL_OTP_SEND_WINDOW_START INTEGER NOT NULL,
                $COL_OTP_FAILED_ATTEMPTS INTEGER NOT NULL DEFAULT 0,
                $COL_OTP_VERIFIED_AT INTEGER,
                $COL_OTP_TOKEN_HASH TEXT,
                $COL_OTP_USED INTEGER NOT NULL DEFAULT 0,
                UNIQUE($COL_OTP_EMAIL, $COL_OTP_PURPOSE),
                FOREIGN KEY ($COL_OTP_USER_ID) REFERENCES $TABLE_USERS($COL_USER_ID) ON DELETE CASCADE
            );
            """.trimIndent()
        )
    }

    private fun createPromotionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PROMOTIONS (
                $COL_PROMO_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PROMO_CODE TEXT NOT NULL UNIQUE,
                $COL_PROMO_NAME TEXT NOT NULL,
                $COL_PROMO_DESCRIPTION TEXT,
                $COL_PROMO_DISCOUNT_TYPE TEXT NOT NULL CHECK ($COL_PROMO_DISCOUNT_TYPE IN ('PERCENTAGE','FIXED_AMOUNT')),
                $COL_PROMO_DISCOUNT_VALUE REAL NOT NULL CHECK ($COL_PROMO_DISCOUNT_VALUE > 0),
                $COL_PROMO_START_DATE TEXT NOT NULL,
                $COL_PROMO_END_DATE TEXT NOT NULL,
                $COL_PROMO_TARGET_TYPE TEXT NOT NULL DEFAULT 'ALL',
                $COL_PROMO_IS_ACTIVE INTEGER NOT NULL DEFAULT 1,
                $COL_PROMO_CREATED_AT TEXT NOT NULL
            );
            """.trimIndent()
        )
    }

    private fun createPromotionTargetsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PROMO_TARGETS (
                $COL_PT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PT_PROMO_ID INTEGER NOT NULL,
                $COL_PT_PROD_ID INTEGER,
                $COL_PT_CAT_ID INTEGER,
                CHECK (($COL_PT_PROD_ID IS NULL) <> ($COL_PT_CAT_ID IS NULL)),
                UNIQUE ($COL_PT_PROMO_ID, $COL_PT_PROD_ID, $COL_PT_CAT_ID),
                FOREIGN KEY ($COL_PT_PROMO_ID) REFERENCES $TABLE_PROMOTIONS($COL_PROMO_ID) ON DELETE CASCADE,
                FOREIGN KEY ($COL_PT_PROD_ID) REFERENCES $TABLE_PRODUCTS($COL_PROD_ID) ON DELETE CASCADE,
                FOREIGN KEY ($COL_PT_CAT_ID) REFERENCES $TABLE_CATEGORIES($COL_CAT_ID) ON DELETE CASCADE
            );
            """.trimIndent()
        )
    }

    private fun createCouponsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_COUPONS (
                $COL_COUPON_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_COUPON_CODE TEXT NOT NULL UNIQUE,
                $COL_COUPON_TITLE TEXT NOT NULL,
                $COL_COUPON_DISCOUNT_TYPE TEXT NOT NULL CHECK ($COL_COUPON_DISCOUNT_TYPE IN ('PERCENTAGE','FIXED_AMOUNT')),
                $COL_COUPON_DISCOUNT_VALUE REAL NOT NULL CHECK ($COL_COUPON_DISCOUNT_VALUE > 0),
                $COL_COUPON_MIN_ORDER REAL NOT NULL DEFAULT 0,
                $COL_COUPON_MAX_DISCOUNT REAL,
                $COL_COUPON_START_DATE TEXT NOT NULL,
                $COL_COUPON_END_DATE TEXT NOT NULL,
                $COL_COUPON_USAGE_LIMIT INTEGER NOT NULL DEFAULT 100 CHECK ($COL_COUPON_USAGE_LIMIT > 0),
                $COL_COUPON_USED_COUNT INTEGER NOT NULL DEFAULT 0 CHECK ($COL_COUPON_USED_COUNT >= 0),
                $COL_COUPON_IS_ACTIVE INTEGER NOT NULL DEFAULT 1,
                $COL_COUPON_CREATED_AT TEXT NOT NULL
            );
            """.trimIndent()
        )
    }

    private fun createNotificationsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NOTIFICATIONS (
                $COL_NOTIF_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NOTIF_USER_ID INTEGER NOT NULL,
                $COL_NOTIF_TITLE TEXT NOT NULL,
                $COL_NOTIF_CONTENT TEXT NOT NULL,
                $COL_NOTIF_TYPE TEXT NOT NULL DEFAULT 'ORDER_DELIVERED',
                $COL_NOTIF_ORDER_ID INTEGER,
                $COL_NOTIF_PROD_ID INTEGER,
                $COL_NOTIF_ACTION TEXT,
                $COL_NOTIF_IS_READ INTEGER NOT NULL DEFAULT 0,
                $COL_NOTIF_CREATED_AT TEXT NOT NULL,
                FOREIGN KEY ($COL_NOTIF_USER_ID) REFERENCES $TABLE_USERS($COL_USER_ID) ON DELETE CASCADE
            );
            """.trimIndent()
        )
    }

    private fun createReviewsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_REVIEWS (
                $COL_REVIEW_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_REVIEW_PROD_ID INTEGER NOT NULL,
                $COL_REVIEW_ORDER_ID INTEGER NOT NULL,
                $COL_REVIEW_USER_ID INTEGER NOT NULL,
                $COL_REVIEW_USER_NAME TEXT NOT NULL,
                $COL_REVIEW_RATING INTEGER NOT NULL CHECK ($COL_REVIEW_RATING BETWEEN 1 AND 5),
                $COL_REVIEW_COMMENT TEXT,
                $COL_REVIEW_IS_VISIBLE INTEGER NOT NULL DEFAULT 1,
                $COL_REVIEW_CREATED_AT TEXT NOT NULL,
                UNIQUE($COL_REVIEW_ORDER_ID, $COL_REVIEW_PROD_ID, $COL_REVIEW_USER_ID),
                FOREIGN KEY ($COL_REVIEW_PROD_ID) REFERENCES $TABLE_PRODUCTS($COL_PROD_ID) ON DELETE CASCADE,
                FOREIGN KEY ($COL_REVIEW_ORDER_ID) REFERENCES $TABLE_ORDERS($COL_ORDER_ID) ON DELETE CASCADE,
                FOREIGN KEY ($COL_REVIEW_USER_ID) REFERENCES $TABLE_USERS($COL_USER_ID) ON DELETE CASCADE
            );
            """.trimIndent()
        )
    }

    private fun createFavoritesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_FAVORITES (
                $COL_FAV_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FAV_USER_ID INTEGER NOT NULL,
                $COL_FAV_PROD_ID INTEGER NOT NULL,
                $COL_FAV_CREATED_AT TEXT NOT NULL,
                UNIQUE($COL_FAV_USER_ID, $COL_FAV_PROD_ID),
                FOREIGN KEY ($COL_FAV_USER_ID) REFERENCES $TABLE_USERS($COL_USER_ID) ON DELETE CASCADE,
                FOREIGN KEY ($COL_FAV_PROD_ID) REFERENCES $TABLE_PRODUCTS($COL_PROD_ID) ON DELETE CASCADE
            );
            """.trimIndent()
        )
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_category ON $TABLE_PRODUCTS($COL_PROD_CATEGORY_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_orders_user ON $TABLE_ORDERS($COL_ORDER_USER_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_orders_status_created ON $TABLE_ORDERS($COL_ORDER_STATUS, $COL_ORDER_CREATED_AT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_order_details_order ON $TABLE_ORDER_DETAILS($COL_DETAIL_ORDER_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_order_details_product ON $TABLE_ORDER_DETAILS($COL_DETAIL_PROD_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_promotions_active_dates ON $TABLE_PROMOTIONS($COL_PROMO_IS_ACTIVE, $COL_PROMO_START_DATE, $COL_PROMO_END_DATE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_promo_targets_promo ON $TABLE_PROMO_TARGETS($COL_PT_PROMO_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_promo_targets_product ON $TABLE_PROMO_TARGETS($COL_PT_PROD_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_promo_targets_category ON $TABLE_PROMO_TARGETS($COL_PT_CAT_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reviews_product ON $TABLE_REVIEWS($COL_REVIEW_PROD_ID, $COL_REVIEW_IS_VISIBLE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_favorites_user ON $TABLE_FAVORITES($COL_FAV_USER_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON $TABLE_NOTIFICATIONS($COL_NOTIF_USER_ID, $COL_NOTIF_IS_READ)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_notifications_event ON $TABLE_NOTIFICATIONS" +
                "($COL_NOTIF_USER_ID, $COL_NOTIF_TYPE, $COL_NOTIF_ORDER_ID) WHERE $COL_NOTIF_ORDER_ID IS NOT NULL"
        )
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) {
                if (c.getString(nameIdx).equals(column, ignoreCase = true)) return true
            }
        }
        return false
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, definition: String) {
        if (!hasColumn(db, table, column)) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
        }
    }

    /** Admin "restore sample data": wipes everything and re-seeds the demo dataset. */
    fun resetToDefaultData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            listOf(
                TABLE_OTP_SESSIONS, TABLE_FAVORITES, TABLE_REVIEWS, TABLE_NOTIFICATIONS, TABLE_PROMO_TARGETS, TABLE_COUPONS,
                TABLE_PROMOTIONS, TABLE_ORDER_DETAILS, TABLE_ORDERS, TABLE_CART, TABLE_PRODUCTS,
                TABLE_CATEGORIES, TABLE_USERS
            ).forEach { db.delete(it, null, null) }
            db.delete("sqlite_sequence", null, null)
            seedInitialData(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        val now = DateUtils.now()

        db.insert(TABLE_USERS, null, ContentValues().apply {
            put(COL_USER_USERNAME, "admin")
            put(COL_USER_FULL_NAME, "Quản Trị Viên Hệ Thống")
            put(COL_USER_EMAIL, "duongtrinhan00@gmail.com")
            put(COL_USER_PHONE, "0988888888")
            put(COL_USER_PASSWORD_HASH, PasswordUtils.hashPassword("admin123"))
            put(COL_USER_ROLE, "ADMIN")
            put(COL_USER_ADDRESS, "Phòng Quản Trị Tầng 8, Tòa Nhà Kim Cương, TP.HCM")
        })

        val customerId = db.insert(TABLE_USERS, null, ContentValues().apply {
            put(COL_USER_USERNAME, "customer1")
            put(COL_USER_FULL_NAME, "Nguyễn Văn An")
            put(COL_USER_EMAIL, "customer1@gmail.com")
            put(COL_USER_PHONE, "0901234567")
            put(COL_USER_PASSWORD_HASH, PasswordUtils.hashPassword("customer123"))
            put(COL_USER_ROLE, "CUSTOMER")
            put(COL_USER_ADDRESS, "123 Đường Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM")
        })

        val categories = listOf(
            "Nhẫn Kim Cương" to "Bộ sưu tập nhẫn đính kim cương và đá quý tự nhiên sang trọng",
            "Dây Chuyền & Mặt Dây" to "Dây chuyền vàng, bạch kim đính mặt đá tinh xảo",
            "Vòng Tay & Lắc Tay" to "Vòng tay phong thủy, lắc tay vàng 18K và bạc Ý cao cấp",
            "Bông Tai Quý Phái" to "Bông tai ngọc trai, nụ kim cương tỏa sáng quyến rũ",
            "Trang Sức Vàng 18K" to "Trang sức chế tác từ vàng chuẩn 18K (750) đẳng cấp",
            "Trang Sức Bạc Ý 925" to "Bạc Ý mạ Rhodium chống xỉn màu và sáng bóng bền lâu"
        )
        val catIds = categories.map { (name, desc) ->
            db.insert(TABLE_CATEGORIES, null, ContentValues().apply {
                put(COL_CAT_NAME, name)
                put(COL_CAT_DESCRIPTION, desc)
            })
        }

        val warrantyGold = "Bảo hành 12 tháng, làm sáng miễn phí trọn đời"
        val warrantySilver = "Bảo hành 6 tháng, đánh bóng miễn phí"
        val products = listOf(
            ProductSeed("Nhẫn Kim Cương Solitaire Hoàng Gia 18K", "Nhẫn đính kim cương tự nhiên 5.4 ly nước D, độ trong VVS1, ổ nhẫn chế tác từ vàng trắng 18K nguyên khối.", 38500000.0, 8, 0, "Vàng trắng 18K & Kim Cương", "1.2 chỉ", "jw_ring_solitaire", "Kim cương tự nhiên 5.4 ly", "18K (75%)", "Ni 10 - 14", warrantyGold),
            ProductSeed("Nhẫn Ruby Huyết Bồ Câu Đính Kim Cương", "Đá chủ Ruby huyết bồ câu tự nhiên 2.5 carat, giác cắt Oval lấp lánh bao quanh bởi 16 viên kim cương tấm.", 52000000.0, 4, 0, "Vàng vàng 18K & Ruby", "1.5 chỉ", "jw_ring_ruby", "Ruby 2.5 carat & kim cương tấm", "18K (75%)", "Ni 9 - 13", warrantyGold),
            ProductSeed("Dây Chuyền Vàng Ý Khắc Hoa Văn Cung Đình", "Dây chuyền mắt xích kép thanh lịch, điểm xuyết họa tiết hoàng gia Ý sắc nét và quý phái.", 18200000.0, 15, 1, "Vàng Ý 750", "2.8 chỉ", "jw_necklace_italy", null, "18K (75%)", "Dài 45 cm", warrantyGold),
            ProductSeed("Mặt Dây Chuyền Trái Tim Sapphire Xanh Biển", "Mặt dây hình trái tim đính đá Sapphire tự nhiên màu xanh sâu thẳm, viền kim cương nhân tạo Swarovski.", 14500000.0, 12, 1, "Vàng trắng 14K & Sapphire", "0.9 chỉ", "jw_pendant_sapphire", "Sapphire tự nhiên", "14K (58.3%)", null, warrantyGold),
            ProductSeed("Lắc Tay Bạc Ý 925 Đính Đá Tinh Khôi", "Lắc tay bạc Ý 925 phủ bạch kim sáng bóng, thiết kế bi tròn xen kẽ hoa tuyết lấp lánh.", 2450000.0, 25, 2, "Bạc Ý 925 mạ Rhodium", "4.2 chỉ", "jw_bracelet_silver", "Đá CZ", "Bạc 92.5%", "Dài 17 cm", warrantySilver),
            ProductSeed("Vòng Tay Bản Liền Vàng 18K Chạm Rồng Phượng", "Vòng tay truyền thống chạm nổi rồng phượng tinh xảo mang ý nghĩa may mắn, tài lộc cho gia chủ.", 46800000.0, 3, 2, "Vàng 18K (75%)", "5.0 chỉ", "jw_bangle_gold", null, "18K (75%)", "Đường kính 58 mm", warrantyGold),
            ProductSeed("Bông Tai Ngọc Trai Akoya Ánh Hồng", "Cặp ngọc trai biển Akoya 8.5mm tròn hoàn hảo ánh xà cừ hồng dịu, chấu vàng trắng 18K cao cấp.", 9800000.0, 10, 3, "Ngọc trai Akoya & Vàng trắng 18K", "0.6 chỉ", "jw_earrings_pearl", "Ngọc trai Akoya 8.5 mm", "18K (75%)", null, warrantyGold),
            ProductSeed("Bông Tai Nụ Kim Cương 4 Chấu Cổ Điển", "Bông tai nụ kim cương thiên nhiên 4.5 ly mỗi bên, thiết kế tinh giản tôn vinh vẻ đẹp thuần khiết.", 28900000.0, 7, 3, "Bạch kim Platinum 950 & Kim cương", "0.8 chỉ", "jw_earrings_stud", "Kim cương 4.5 ly", "Platinum 950", null, warrantyGold),
            ProductSeed("Kiềng Cưới Vàng 24K Hoa Mai May Mắn", "Kiềng cưới cô dâu vàng ta 24K (999.9) chạm khắc chùm hoa mai 5 cánh sum vầy hạnh phúc.", 68000000.0, 5, 4, "Vàng 24K (999.9)", "7.5 chỉ", "jw_kieng_gold", null, "24K (99.99%)", null, warrantyGold),
            ProductSeed("Dây Chuyền Nam Vàng 18K Mắt Xích Cuba", "Dây chuyền bản to đầm tay phong cách mạnh mẽ, khóa hộp kép chắc chắn và nam tính.", 62500000.0, 2, 4, "Vàng 18K nguyên khối", "8.2 chỉ", "jw_chain_men", null, "18K (75%)", "Dài 55 cm", warrantyGold),
            ProductSeed("Nhẫn Cặp Bạc Ý 925 Khắc Tên Vĩnh Cửu", "Cặp nhẫn bạc đôi thiết kế tối giản trẻ trung, bề mặt phay xước hiện đại, miễn phí khắc tên theo yêu cầu.", 1850000.0, 30, 5, "Bạc Ý 925", "2.1 chỉ", "jw_rings_couple", null, "Bạc 92.5%", "Ni 6 - 20", warrantySilver),
            ProductSeed("Lắc Chân Bạc Nữ Chuông Nhỏ Dễ Thương", "Lắc chân bạc 925 phối chuông nhỏ ngân vang nhẹ nhàng khi di chuyển, tôn dáng cổ chân thanh mảnh.", 950000.0, 0, 5, "Bạc Ý 925", "1.5 chỉ", "jw_anklet_silver", null, "Bạc 92.5%", "Dài 22 cm", null)
        )
        val prodIds = products.map { p ->
            db.insert(TABLE_PRODUCTS, null, ContentValues().apply {
                put(COL_PROD_NAME, p.name)
                put(COL_PROD_DESCRIPTION, p.description)
                put(COL_PROD_PRICE, p.price)
                put(COL_PROD_QUANTITY, p.quantity)
                put(COL_PROD_CATEGORY_ID, catIds[p.categoryIndex])
                put(COL_PROD_MATERIAL, p.material)
                put(COL_PROD_WEIGHT, p.weight)
                put(COL_PROD_IMAGE_NAME, p.imageName)
                put(COL_PROD_CREATED_AT, now)
                put(COL_PROD_GEM_TYPE, p.gemType)
                put(COL_PROD_PURITY, p.purity)
                put(COL_PROD_SIZE, p.size)
                put(COL_PROD_WARRANTY, p.warranty)
            })
        }

        if (customerId <= 0) return

        // Delivered order (already reviewed & notified) - used by revenue statistics.
        val deliveredOrderId = insertSeedOrder(db, customerId, STATUS_DELIVERED, "Đã thanh toán", now,
            listOf(Triple(prodIds[0], products[0], 1)))
        // Order in delivery with two products: lets the admin test "Đã giao hàng" -> notification -> review.
        insertSeedOrder(db, customerId, STATUS_SHIPPING, "Chưa thanh toán", now,
            listOf(Triple(prodIds[6], products[6], 1), Triple(prodIds[4], products[4], 2)))

        seedPromotionsAndCoupons(db, catIds, prodIds)

        if (deliveredOrderId > 0) {
            db.insert(TABLE_REVIEWS, null, ContentValues().apply {
                put(COL_REVIEW_PROD_ID, prodIds[0])
                put(COL_REVIEW_ORDER_ID, deliveredOrderId)
                put(COL_REVIEW_USER_ID, customerId)
                put(COL_REVIEW_USER_NAME, "Nguyễn Văn An")
                put(COL_REVIEW_RATING, 5)
                put(COL_REVIEW_COMMENT, "Nhẫn kim cương ngoài đời lấp lánh hơn cả trong hình, giác cắt xuất sắc, đóng gói hộp nhung rất cao cấp!")
                put(COL_REVIEW_IS_VISIBLE, 1)
                put(COL_REVIEW_CREATED_AT, now)
            })
            db.insert(TABLE_NOTIFICATIONS, null, ContentValues().apply {
                put(COL_NOTIF_USER_ID, customerId)
                put(COL_NOTIF_TITLE, "Đơn hàng #$deliveredOrderId đã được giao thành công!")
                put(COL_NOTIF_CONTENT, "Đơn hàng của bạn đã được giao thành công. Cảm ơn bạn đã mua sắm tại JewelryManagement! Bạn có muốn đánh giá sản phẩm vừa mua không?")
                put(COL_NOTIF_TYPE, "ORDER_DELIVERED")
                put(COL_NOTIF_ORDER_ID, deliveredOrderId)
                put(COL_NOTIF_PROD_ID, prodIds[0])
                put(COL_NOTIF_ACTION, "REVIEW_ORDER")
                put(COL_NOTIF_IS_READ, 1)
                put(COL_NOTIF_CREATED_AT, now)
            })
        }

        listOf(prodIds[0], prodIds[2]).forEach { pid ->
            db.insert(TABLE_FAVORITES, null, ContentValues().apply {
                put(COL_FAV_USER_ID, customerId)
                put(COL_FAV_PROD_ID, pid)
                put(COL_FAV_CREATED_AT, now)
            })
        }
    }

    private fun insertSeedOrder(
        db: SQLiteDatabase,
        userId: Long,
        status: String,
        paymentStatus: String,
        createdAt: String,
        lines: List<Triple<Long, ProductSeed, Int>>
    ): Long {
        val total = lines.sumOf { it.second.price * it.third }
        val orderId = db.insert(TABLE_ORDERS, null, ContentValues().apply {
            put(COL_ORDER_USER_ID, userId)
            put(COL_ORDER_RECEIVER_NAME, "Nguyễn Văn An")
            put(COL_ORDER_PHONE, "0901234567")
            put(COL_ORDER_SHIPPING_ADDRESS, "123 Đường Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM")
            put(COL_ORDER_TOTAL_AMOUNT, total)
            put(COL_ORDER_DISCOUNT_AMOUNT, 0.0)
            put(COL_ORDER_FINAL_AMOUNT, total)
            put(COL_ORDER_PAYMENT_METHOD, "COD")
            put(COL_ORDER_PAYMENT_STATUS, paymentStatus)
            put(COL_ORDER_STATUS, status)
            put(COL_ORDER_CREATED_AT, createdAt)
        })
        if (orderId <= 0) return orderId
        lines.forEach { (productId, seed, qty) ->
            db.insert(TABLE_ORDER_DETAILS, null, ContentValues().apply {
                put(COL_DETAIL_ORDER_ID, orderId)
                put(COL_DETAIL_PROD_ID, productId)
                put(COL_DETAIL_PROD_NAME, seed.name)
                put(COL_DETAIL_QUANTITY, qty)
                put(COL_DETAIL_UNIT_PRICE, seed.price)
                put(COL_DETAIL_ORIGINAL_PRICE, seed.price)
                put(COL_DETAIL_SUBTOTAL, seed.price * qty)
            })
        }
        return orderId
    }

    /** Demo promotions/coupons with dates relative to install time, covering every status. */
    private fun seedPromotionsAndCoupons(db: SQLiteDatabase, catIds: List<Long>, prodIds: List<Long>) {
        val now = DateUtils.now()
        fun shifted(days: Int, endOfDay: Boolean = false): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, days)
            if (endOfDay) {
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            }
            return DateUtils.format(cal.time)
        }

        fun promo(code: String, name: String, desc: String, type: String, value: Double, start: String, end: String,
                  targetType: String, categoryIds: List<Long> = emptyList(), productIds: List<Long> = emptyList()) {
            val id = db.insert(TABLE_PROMOTIONS, null, ContentValues().apply {
                put(COL_PROMO_CODE, code)
                put(COL_PROMO_NAME, name)
                put(COL_PROMO_DESCRIPTION, desc)
                put(COL_PROMO_DISCOUNT_TYPE, type)
                put(COL_PROMO_DISCOUNT_VALUE, value)
                put(COL_PROMO_START_DATE, start)
                put(COL_PROMO_END_DATE, end)
                put(COL_PROMO_TARGET_TYPE, targetType)
                put(COL_PROMO_IS_ACTIVE, 1)
                put(COL_PROMO_CREATED_AT, now)
            })
            if (id <= 0) return
            categoryIds.forEach { cid ->
                db.insert(TABLE_PROMO_TARGETS, null, ContentValues().apply { put(COL_PT_PROMO_ID, id); put(COL_PT_CAT_ID, cid) })
            }
            productIds.forEach { pid ->
                db.insert(TABLE_PROMO_TARGETS, null, ContentValues().apply { put(COL_PT_PROMO_ID, id); put(COL_PT_PROD_ID, pid) })
            }
        }

        promo("PROMO_GOLD_WEEK", "Tuần Lễ Vàng Trang Sức 18K", "Giảm ngay 1.000.000 đ cho sản phẩm Trang Sức Vàng 18K",
            "FIXED_AMOUNT", 1000000.0, shifted(-7), shifted(30, true), "CATEGORY", categoryIds = listOf(catIds[4]))
        promo("PROMO_SILVER_10", "Ưu đãi Bạc Ý 925", "Giảm 10% toàn bộ trang sức Bạc Ý 925",
            "PERCENTAGE", 10.0, shifted(-7), shifted(30, true), "CATEGORY", categoryIds = listOf(catIds[5]))
        promo("PROMO_COUPLE_RING", "Nhẫn Cặp Tình Yêu", "Giảm 20% riêng cho Nhẫn Cặp Bạc Ý (trùng với ưu đãi Bạc Ý: hệ thống chọn mức có lợi nhất)",
            "PERCENTAGE", 20.0, shifted(-7), shifted(30, true), "PRODUCT", productIds = listOf(prodIds[10]))
        promo("PROMO_YEAR_END", "Ưu đãi trang sức cuối năm", "Giảm 15% cho danh mục Nhẫn Kim Cương",
            "PERCENTAGE", 15.0, shifted(30), shifted(40, true), "CATEGORY", categoryIds = listOf(catIds[0]))
        promo("PROMO_ENDED_DEMO", "Flash Sale đã kết thúc", "Chương trình đã hết hạn (minh họa trạng thái Đã kết thúc)",
            "PERCENTAGE", 25.0, shifted(-60), shifted(-30, true), "ALL")

        fun coupon(code: String, title: String, type: String, value: Double, min: Double, max: Double?,
                   start: String, end: String, limit: Int, used: Int) {
            db.insert(TABLE_COUPONS, null, ContentValues().apply {
                put(COL_COUPON_CODE, code)
                put(COL_COUPON_TITLE, title)
                put(COL_COUPON_DISCOUNT_TYPE, type)
                put(COL_COUPON_DISCOUNT_VALUE, value)
                put(COL_COUPON_MIN_ORDER, min)
                if (max != null) put(COL_COUPON_MAX_DISCOUNT, max) else putNull(COL_COUPON_MAX_DISCOUNT)
                put(COL_COUPON_START_DATE, start)
                put(COL_COUPON_END_DATE, end)
                put(COL_COUPON_USAGE_LIMIT, limit)
                put(COL_COUPON_USED_COUNT, used)
                put(COL_COUPON_IS_ACTIVE, 1)
                put(COL_COUPON_CREATED_AT, now)
            })
        }

        coupon("JEWELRY10", "Khách hàng thân thiết - giảm 10%", "PERCENTAGE", 10.0, 5000000.0, 2000000.0, shifted(-30), shifted(90, true), 100, 0)
        coupon("WELCOME500", "Chào mừng thành viên mới - giảm 500.000 đ", "FIXED_AMOUNT", 500000.0, 2000000.0, null, shifted(-30), shifted(90, true), 200, 0)
        coupon("GOLDVIP", "Đặc quyền VIP - giảm 2.000.000 đ", "FIXED_AMOUNT", 2000000.0, 20000000.0, null, shifted(-30), shifted(90, true), 50, 0)
        coupon("NOEL2026", "Giáng sinh (chưa bắt đầu)", "PERCENTAGE", 12.0, 1000000.0, 3000000.0, shifted(45), shifted(60, true), 100, 0)
        coupon("SUMMER2026", "Hè rực rỡ (đã hết hạn)", "PERCENTAGE", 15.0, 1000000.0, 3000000.0, shifted(-90), shifted(-31, true), 100, 3)
        coupon("LASTONE", "Mã giới hạn 1 lượt (đã dùng hết)", "FIXED_AMOUNT", 300000.0, 0.0, null, shifted(-30), shifted(90, true), 1, 1)
    }

    private data class ProductSeed(
        val name: String,
        val description: String,
        val price: Double,
        val quantity: Int,
        val categoryIndex: Int,
        val material: String,
        val weight: String,
        val imageName: String,
        val gemType: String?,
        val purity: String?,
        val size: String?,
        val warranty: String?
    )
}
