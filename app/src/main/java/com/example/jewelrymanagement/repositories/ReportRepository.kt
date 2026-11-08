package com.example.jewelrymanagement.repositories

import android.content.Context
import com.example.jewelrymanagement.database.CouponDao
import com.example.jewelrymanagement.database.DatabaseHelper
import com.example.jewelrymanagement.database.OrderDao
import com.example.jewelrymanagement.database.ProductDao
import com.example.jewelrymanagement.database.PromotionDao
import com.example.jewelrymanagement.models.Promotion
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.DateUtils
import java.util.Locale

enum class ReportType(val label: String, val fileSlug: String) {
    REVENUE("Doanh thu", "DoanhThu"),
    ORDERS("Đơn hàng", "DonHang"),
    PRODUCTS("Sản phẩm bán ra", "SanPham"),
    INVENTORY("Tồn kho", "TonKho"),
    PROMOTIONS("Khuyến mãi & Coupon", "KhuyenMai"),
    REVIEWS("Đánh giá", "DanhGia"),
    ALL("Tổng hợp tất cả", "TongHop")
}

/** Typed cell so PDF shows formatted text while Excel stores real numbers/dates. */
sealed class ReportCell {
    data class Text(val value: String) : ReportCell()
    data class Money(val value: Double) : ReportCell()
    data class Count(val value: Long) : ReportCell()
    data class Decimal(val value: Double) : ReportCell()
    /** Stored as "yyyy-MM-dd HH:mm:ss" or "yyyy-MM-dd". */
    data class DateValue(val raw: String) : ReportCell()

    fun display(): String = when (this) {
        is Text -> value
        is Money -> CurrencyUtils.formatVnd(value)
        is Count -> String.format(Locale.forLanguageTag("vi-VN"), "%,d", value)
        is Decimal -> String.format(Locale.US, "%.1f", value)
        is DateValue -> if (raw.length <= 10) DateUtils.dayToDisplay(raw) else DateUtils.toDisplay(raw)
    }

    val isNumeric: Boolean get() = this is Money || this is Count || this is Decimal
}

data class ReportColumn(val header: String, val weight: Float)

data class ReportTable(
    val title: String,
    /** Excel sheet name (max 31 chars, no []:*?/\). */
    val sheetName: String,
    val columns: List<ReportColumn>,
    val rows: List<List<ReportCell>>,
    val totals: List<ReportCell>? = null,
    val note: String? = null
)

data class Report(
    val type: ReportType,
    val startDay: String,
    val endDay: String,
    val statusLabel: String,
    val generatedAt: String,
    val summary: List<Pair<String, String>>,
    val tables: List<ReportTable>
) {
    val title: String get() = "Báo cáo ${type.label.lowercase(Locale.forLanguageTag("vi-VN"))}"
    val isEmpty: Boolean get() = tables.all { it.rows.isEmpty() }
}

class ReportRepository(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)
    private val orderDao = OrderDao(context)
    private val productDao = ProductDao(context)
    private val promotionDao = PromotionDao(context)
    private val couponDao = CouponDao(context)

    companion object {
        const val STATUS_ALL = "ALL"
        private const val LOW_STOCK = 5
    }

    /** [startDay]/[endDay] are "yyyy-MM-dd" and inclusive; [status] is an order status or [STATUS_ALL]. */
    fun build(type: ReportType, startDay: String, endDay: String, status: String): Report {
        require(startDay <= endDay) { "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc" }
        val summary = mutableListOf<Pair<String, String>>()
        val tables = mutableListOf<ReportTable>()
        val types = if (type == ReportType.ALL) ReportType.entries.filter { it != ReportType.ALL } else listOf(type)
        for (t in types) {
            when (t) {
                ReportType.REVENUE -> revenue(startDay, endDay, summary, tables)
                ReportType.ORDERS -> orders(startDay, endDay, status, summary, tables)
                ReportType.PRODUCTS -> products(startDay, endDay, status, tables)
                ReportType.INVENTORY -> inventory(summary, tables)
                ReportType.PROMOTIONS -> promotions(startDay, endDay, tables)
                ReportType.REVIEWS -> reviews(startDay, endDay, summary, tables)
                ReportType.ALL -> Unit
            }
        }
        return Report(
            type = type,
            startDay = startDay,
            endDay = endDay,
            statusLabel = if (status == STATUS_ALL) "Tất cả trạng thái" else status,
            generatedAt = DateUtils.now(),
            summary = summary,
            tables = tables
        )
    }

    private fun range(startDay: String, endDay: String) = arrayOf("$startDay 00:00:00", "$endDay 23:59:59")

    private fun revenue(startDay: String, endDay: String, summary: MutableList<Pair<String, String>>, tables: MutableList<ReportTable>) {
        val rows = mutableListOf<List<ReportCell>>()
        var orders = 0L
        var gross = 0.0
        var promo = 0.0
        var coupon = 0.0
        var net = 0.0
        val sql = """
            SELECT substr(${DatabaseHelper.COL_ORDER_CREATED_AT}, 1, 10) AS d, COUNT(*),
                   SUM(${DatabaseHelper.COL_ORDER_TOTAL_AMOUNT}),
                   SUM(COALESCE(${DatabaseHelper.COL_ORDER_PROMO_DISCOUNT}, 0)),
                   SUM(COALESCE(${DatabaseHelper.COL_ORDER_COUPON_DISCOUNT}, 0)),
                   SUM(CASE WHEN ${DatabaseHelper.COL_ORDER_FINAL_AMOUNT} > 0 THEN ${DatabaseHelper.COL_ORDER_FINAL_AMOUNT}
                            ELSE ${DatabaseHelper.COL_ORDER_TOTAL_AMOUNT} - COALESCE(${DatabaseHelper.COL_ORDER_DISCOUNT_AMOUNT}, 0) END)
            FROM ${DatabaseHelper.TABLE_ORDERS}
            WHERE ${DatabaseHelper.COL_ORDER_STATUS} = ? AND ${DatabaseHelper.COL_ORDER_CREATED_AT} BETWEEN ? AND ?
            GROUP BY d ORDER BY d
        """.trimIndent()
        dbHelper.readableDatabase.rawQuery(sql, arrayOf(DatabaseHelper.STATUS_DELIVERED, *range(startDay, endDay))).use { c ->
            while (c.moveToNext()) {
                rows.add(
                    listOf(
                        ReportCell.DateValue(c.getString(0)),
                        ReportCell.Count(c.getLong(1)),
                        ReportCell.Money(c.getDouble(2)),
                        ReportCell.Money(c.getDouble(3)),
                        ReportCell.Money(c.getDouble(4)),
                        ReportCell.Money(c.getDouble(5))
                    )
                )
                orders += c.getLong(1); gross += c.getDouble(2); promo += c.getDouble(3); coupon += c.getDouble(4); net += c.getDouble(5)
            }
        }
        val cancelled = countOrders(startDay, endDay, DatabaseHelper.STATUS_CANCELLED)
        summary += "Đơn đã giao trong kỳ" to orders.toString()
        summary += "Đơn đã hủy (không tính doanh thu)" to cancelled.toString()
        summary += "Tổng tiền hàng (giá gốc)" to CurrencyUtils.formatVnd(gross)
        summary += "Tổng giảm giá (sự kiện + coupon)" to CurrencyUtils.formatVnd(promo + coupon)
        summary += "Doanh thu thuần" to CurrencyUtils.formatVnd(net)

        tables += ReportTable(
            title = "Doanh thu theo ngày (chỉ đơn đã giao)",
            sheetName = "DoanhThu",
            columns = listOf(
                ReportColumn("Ngày", 1.1f), ReportColumn("Số đơn", 0.7f), ReportColumn("Tiền hàng", 1.4f),
                ReportColumn("Giảm sự kiện", 1.3f), ReportColumn("Giảm coupon", 1.3f), ReportColumn("Doanh thu thuần", 1.5f)
            ),
            rows = rows,
            totals = listOf(
                ReportCell.Text("Tổng cộng"), ReportCell.Count(orders), ReportCell.Money(gross),
                ReportCell.Money(promo), ReportCell.Money(coupon), ReportCell.Money(net)
            )
        )
    }

    private fun countOrders(startDay: String, endDay: String, status: String): Long {
        dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_ORDERS} WHERE ${DatabaseHelper.COL_ORDER_STATUS} = ? AND ${DatabaseHelper.COL_ORDER_CREATED_AT} BETWEEN ? AND ?",
            arrayOf(status, *range(startDay, endDay))
        ).use { c -> return if (c.moveToFirst()) c.getLong(0) else 0 }
    }

    private fun orders(startDay: String, endDay: String, status: String, summary: MutableList<Pair<String, String>>, tables: MutableList<ReportTable>) {
        val list = orderDao.getOrdersByDateRange(startDay, endDay, status)
        val byStatus = list.groupingBy { it.orderStatus }.eachCount()
        summary += "Tổng số đơn (${if (status == STATUS_ALL) "mọi trạng thái" else status})" to list.size.toString()
        DatabaseHelper.ORDER_STATUSES.forEach { s -> byStatus[s]?.let { summary += "  • $s" to it.toString() } }

        tables += ReportTable(
            title = "Danh sách đơn hàng",
            sheetName = "DonHang",
            columns = listOf(
                ReportColumn("Mã", 0.5f), ReportColumn("Ngày đặt", 1.2f), ReportColumn("Khách hàng", 1.4f),
                ReportColumn("Trạng thái", 1.1f), ReportColumn("Thanh toán", 1.1f), ReportColumn("Tiền hàng", 1.2f),
                ReportColumn("Giảm giá", 1.1f), ReportColumn("Thành tiền", 1.2f)
            ),
            rows = list.map { o ->
                listOf(
                    ReportCell.Text("#${o.id}"), ReportCell.DateValue(o.createdAt), ReportCell.Text(o.receiverName),
                    ReportCell.Text(o.orderStatus), ReportCell.Text(o.paymentMethod), ReportCell.Money(o.totalAmount),
                    ReportCell.Money(o.discountAmount), ReportCell.Money(o.finalAmount)
                )
            },
            totals = listOf(
                ReportCell.Text("Tổng"), ReportCell.Text("${list.size} đơn"), ReportCell.Text(""), ReportCell.Text(""), ReportCell.Text(""),
                ReportCell.Money(list.sumOf { it.totalAmount }), ReportCell.Money(list.sumOf { it.discountAmount }),
                ReportCell.Money(list.sumOf { it.finalAmount })
            ),
            note = "Tổng của bảng gồm mọi đơn được liệt kê (kể cả đơn hủy nếu có trong bộ lọc); doanh thu thực xem ở báo cáo Doanh thu."
        )
    }

    private fun products(startDay: String, endDay: String, status: String, tables: MutableList<ReportTable>) {
        val statusCond = if (status == STATUS_ALL) "o.${DatabaseHelper.COL_ORDER_STATUS} <> ?" else "o.${DatabaseHelper.COL_ORDER_STATUS} = ?"
        val statusArg = if (status == STATUS_ALL) DatabaseHelper.STATUS_CANCELLED else status
        val sql = """
            SELECT d.${DatabaseHelper.COL_DETAIL_PROD_NAME}, COALESCE(c.${DatabaseHelper.COL_CAT_NAME}, ''),
                   SUM(d.${DatabaseHelper.COL_DETAIL_QUANTITY}),
                   SUM(d.${DatabaseHelper.COL_DETAIL_QUANTITY} * COALESCE(d.${DatabaseHelper.COL_DETAIL_ORIGINAL_PRICE}, d.${DatabaseHelper.COL_DETAIL_UNIT_PRICE})),
                   SUM(COALESCE(d.${DatabaseHelper.COL_DETAIL_PROMO_DISCOUNT}, 0)),
                   SUM(d.${DatabaseHelper.COL_DETAIL_SUBTOTAL})
            FROM ${DatabaseHelper.TABLE_ORDER_DETAILS} d
            INNER JOIN ${DatabaseHelper.TABLE_ORDERS} o ON o.${DatabaseHelper.COL_ORDER_ID} = d.${DatabaseHelper.COL_DETAIL_ORDER_ID}
            LEFT JOIN ${DatabaseHelper.TABLE_PRODUCTS} p ON p.${DatabaseHelper.COL_PROD_ID} = d.${DatabaseHelper.COL_DETAIL_PROD_ID}
            LEFT JOIN ${DatabaseHelper.TABLE_CATEGORIES} c ON c.${DatabaseHelper.COL_CAT_ID} = p.${DatabaseHelper.COL_PROD_CATEGORY_ID}
            WHERE $statusCond AND o.${DatabaseHelper.COL_ORDER_CREATED_AT} BETWEEN ? AND ?
            GROUP BY COALESCE(d.${DatabaseHelper.COL_DETAIL_PROD_ID}, -d.${DatabaseHelper.COL_DETAIL_ID}), d.${DatabaseHelper.COL_DETAIL_PROD_NAME}
            ORDER BY SUM(d.${DatabaseHelper.COL_DETAIL_QUANTITY}) DESC, d.${DatabaseHelper.COL_DETAIL_PROD_NAME}
        """.trimIndent()
        val rows = mutableListOf<List<ReportCell>>()
        var qty = 0L
        var gross = 0.0
        var promo = 0.0
        var net = 0.0
        dbHelper.readableDatabase.rawQuery(sql, arrayOf(statusArg, *range(startDay, endDay))).use { c ->
            while (c.moveToNext()) {
                rows.add(
                    listOf(
                        ReportCell.Text(c.getString(0)), ReportCell.Text(c.getString(1).ifBlank { "—" }), ReportCell.Count(c.getLong(2)),
                        ReportCell.Money(c.getDouble(3)), ReportCell.Money(c.getDouble(4)), ReportCell.Money(c.getDouble(5))
                    )
                )
                qty += c.getLong(2); gross += c.getDouble(3); promo += c.getDouble(4); net += c.getDouble(5)
            }
        }
        tables += ReportTable(
            title = "Sản phẩm bán ra (${if (status == STATUS_ALL) "trừ đơn đã hủy" else status})",
            sheetName = "SanPham",
            columns = listOf(
                ReportColumn("Sản phẩm", 2.4f), ReportColumn("Danh mục", 1.3f), ReportColumn("SL", 0.6f),
                ReportColumn("Tiền hàng", 1.3f), ReportColumn("Giảm sự kiện", 1.2f), ReportColumn("Sau giảm", 1.3f)
            ),
            rows = rows,
            totals = listOf(
                ReportCell.Text("Tổng cộng"), ReportCell.Text(""), ReportCell.Count(qty),
                ReportCell.Money(gross), ReportCell.Money(promo), ReportCell.Money(net)
            ),
            note = "Giảm giá coupon tính trên cả đơn hàng nên không phân bổ vào từng sản phẩm."
        )
    }

    private fun inventory(summary: MutableList<Pair<String, String>>, tables: MutableList<ReportTable>) {
        val products = productDao.getAll().sortedWith(compareBy({ it.categoryName }, { it.name }))
        summary += "Số mặt hàng" to products.size.toString()
        summary += "Hết hàng" to products.count { it.quantity <= 0 }.toString()
        summary += "Sắp hết (≤ $LOW_STOCK)" to products.count { it.quantity in 1..LOW_STOCK }.toString()
        tables += ReportTable(
            title = "Tồn kho hiện tại (tại thời điểm xuất)",
            sheetName = "TonKho",
            columns = listOf(
                ReportColumn("Mã", 0.5f), ReportColumn("Sản phẩm", 2.4f), ReportColumn("Danh mục", 1.3f),
                ReportColumn("Giá gốc", 1.2f), ReportColumn("Tồn", 0.6f), ReportColumn("Giá trị tồn", 1.4f), ReportColumn("Tình trạng", 1.1f)
            ),
            rows = products.map { p ->
                listOf(
                    ReportCell.Text("SP${p.id}"), ReportCell.Text(p.name), ReportCell.Text(p.categoryName.ifBlank { "—" }),
                    ReportCell.Money(p.price), ReportCell.Count(p.quantity.toLong()), ReportCell.Money(p.price * p.quantity.coerceAtLeast(0)),
                    ReportCell.Text(
                        when {
                            p.isDiscontinued -> "Ngừng KD"
                            p.quantity <= 0 -> "Hết hàng"
                            p.quantity <= LOW_STOCK -> "Sắp hết"
                            else -> "Còn hàng"
                        }
                    )
                )
            },
            totals = listOf(
                ReportCell.Text("Tổng"), ReportCell.Text("${products.size} mặt hàng"), ReportCell.Text(""), ReportCell.Text(""),
                ReportCell.Count(products.sumOf { it.quantity.coerceAtLeast(0).toLong() }),
                ReportCell.Money(products.sumOf { it.price * it.quantity.coerceAtLeast(0) }), ReportCell.Text("")
            )
        )
    }

    private fun promotions(startDay: String, endDay: String, tables: MutableList<ReportTable>) {
        val db = dbHelper.readableDatabase
        val promos = promotionDao.getPromotionsOverlapping(startDay, endDay)
        var totalPromoDiscount = 0.0
        val promoRows = promos.map { p ->
            var qty = 0L
            var discount = 0.0
            db.rawQuery(
                """
                SELECT SUM(d.${DatabaseHelper.COL_DETAIL_QUANTITY}), SUM(COALESCE(d.${DatabaseHelper.COL_DETAIL_PROMO_DISCOUNT}, 0))
                FROM ${DatabaseHelper.TABLE_ORDER_DETAILS} d
                INNER JOIN ${DatabaseHelper.TABLE_ORDERS} o ON o.${DatabaseHelper.COL_ORDER_ID} = d.${DatabaseHelper.COL_DETAIL_ORDER_ID}
                WHERE d.${DatabaseHelper.COL_DETAIL_PROMO_NAME} = ? AND o.${DatabaseHelper.COL_ORDER_STATUS} <> ?
                  AND o.${DatabaseHelper.COL_ORDER_CREATED_AT} BETWEEN ? AND ?
                """.trimIndent(),
                arrayOf(p.name, DatabaseHelper.STATUS_CANCELLED, *range(startDay, endDay))
            ).use { c -> if (c.moveToFirst()) { qty = c.getLong(0); discount = c.getDouble(1) } }
            totalPromoDiscount += discount
            val target = when (p.targetType) {
                Promotion.TARGET_CATEGORY -> "DM: ${p.targetNames.joinToString(", ")}"
                Promotion.TARGET_PRODUCT -> "SP: ${p.targetNames.joinToString(", ")}"
                else -> "Toàn bộ"
            }
            listOf(
                ReportCell.Text(p.code), ReportCell.Text(p.name), ReportCell.Text(p.discountLabel()), ReportCell.Text(target),
                ReportCell.DateValue(p.startDate), ReportCell.DateValue(p.endDate), ReportCell.Text(p.getStatus()),
                ReportCell.Count(qty), ReportCell.Money(discount)
            )
        }
        tables += ReportTable(
            title = "Sự kiện khuyến mãi trong kỳ",
            sheetName = "KhuyenMai",
            columns = listOf(
                ReportColumn("Mã", 1.0f), ReportColumn("Tên", 1.6f), ReportColumn("Mức giảm", 0.9f), ReportColumn("Áp dụng", 1.4f),
                ReportColumn("Bắt đầu", 1.1f), ReportColumn("Kết thúc", 1.1f), ReportColumn("Trạng thái", 0.9f),
                ReportColumn("SL bán", 0.6f), ReportColumn("Tổng giảm", 1.1f)
            ),
            rows = promoRows,
            totals = listOf(
                ReportCell.Text("Tổng"), ReportCell.Text("${promos.size} sự kiện"), ReportCell.Text(""), ReportCell.Text(""),
                ReportCell.Text(""), ReportCell.Text(""), ReportCell.Text(""), ReportCell.Text(""), ReportCell.Money(totalPromoDiscount)
            )
        )

        val coupons = couponDao.getCouponsOverlapping(startDay, endDay)
        var totalCouponDiscount = 0.0
        val couponRows = coupons.map { cp ->
            var orders = 0L
            var discount = 0.0
            db.rawQuery(
                "SELECT COUNT(*), SUM(COALESCE(${DatabaseHelper.COL_ORDER_COUPON_DISCOUNT}, 0)) FROM ${DatabaseHelper.TABLE_ORDERS} " +
                    "WHERE ${DatabaseHelper.COL_ORDER_COUPON_CODE} = ? AND ${DatabaseHelper.COL_ORDER_STATUS} <> ? AND ${DatabaseHelper.COL_ORDER_CREATED_AT} BETWEEN ? AND ?",
                arrayOf(cp.code, DatabaseHelper.STATUS_CANCELLED, *range(startDay, endDay))
            ).use { c -> if (c.moveToFirst()) { orders = c.getLong(0); discount = c.getDouble(1) } }
            totalCouponDiscount += discount
            listOf(
                ReportCell.Text(cp.code), ReportCell.Text(cp.title), ReportCell.Text(cp.discountLabel()), ReportCell.Money(cp.minOrder),
                ReportCell.DateValue(cp.startDate), ReportCell.DateValue(cp.endDate), ReportCell.Text("${cp.usedCount}/${cp.usageLimit}"),
                ReportCell.Text(cp.getStatus()), ReportCell.Count(orders), ReportCell.Money(discount)
            )
        }
        tables += ReportTable(
            title = "Mã giảm giá (coupon) trong kỳ",
            sheetName = "Coupon",
            columns = listOf(
                ReportColumn("Mã", 1.0f), ReportColumn("Tiêu đề", 1.4f), ReportColumn("Mức giảm", 1.2f), ReportColumn("Đơn tối thiểu", 1.0f),
                ReportColumn("Bắt đầu", 1.0f), ReportColumn("Kết thúc", 1.0f), ReportColumn("Đã dùng", 0.7f),
                ReportColumn("Trạng thái", 0.9f), ReportColumn("Đơn trong kỳ", 0.7f), ReportColumn("Tổng giảm", 1.0f)
            ),
            rows = couponRows,
            totals = listOf(
                ReportCell.Text("Tổng"), ReportCell.Text("${coupons.size} mã"), ReportCell.Text(""), ReportCell.Text(""), ReportCell.Text(""),
                ReportCell.Text(""), ReportCell.Text(""), ReportCell.Text(""),
                ReportCell.Count(couponRows.sumOf { (it[8] as ReportCell.Count).value }), ReportCell.Money(totalCouponDiscount)
            ),
            note = "Số liệu sử dụng chỉ tính đơn chưa bị hủy, đặt trong khoảng thời gian báo cáo."
        )
    }

    private fun reviews(startDay: String, endDay: String, summary: MutableList<Pair<String, String>>, tables: MutableList<ReportTable>) {
        val rows = mutableListOf<List<ReportCell>>()
        val stars = IntArray(6)
        val sql = """
            SELECT r.${DatabaseHelper.COL_REVIEW_CREATED_AT}, COALESCE(p.${DatabaseHelper.COL_PROD_NAME}, '(Sản phẩm đã xóa)'),
                   r.${DatabaseHelper.COL_REVIEW_USER_NAME}, r.${DatabaseHelper.COL_REVIEW_ORDER_ID}, r.${DatabaseHelper.COL_REVIEW_RATING},
                   COALESCE(r.${DatabaseHelper.COL_REVIEW_COMMENT}, ''), r.${DatabaseHelper.COL_REVIEW_IS_VISIBLE}
            FROM ${DatabaseHelper.TABLE_REVIEWS} r
            LEFT JOIN ${DatabaseHelper.TABLE_PRODUCTS} p ON p.${DatabaseHelper.COL_PROD_ID} = r.${DatabaseHelper.COL_REVIEW_PROD_ID}
            WHERE r.${DatabaseHelper.COL_REVIEW_CREATED_AT} BETWEEN ? AND ?
            ORDER BY r.${DatabaseHelper.COL_REVIEW_CREATED_AT}
        """.trimIndent()
        dbHelper.readableDatabase.rawQuery(sql, range(startDay, endDay)).use { c ->
            while (c.moveToNext()) {
                val rating = c.getInt(4).coerceIn(1, 5)
                stars[rating]++
                rows.add(
                    listOf(
                        ReportCell.DateValue(c.getString(0)), ReportCell.Text(c.getString(1)), ReportCell.Text(c.getString(2)),
                        ReportCell.Text("#${c.getLong(3)}"), ReportCell.Count(rating.toLong()),
                        ReportCell.Text(c.getString(5).ifBlank { "—" }), ReportCell.Text(if (c.getInt(6) == 1) "Hiển thị" else "Đã ẩn")
                    )
                )
            }
        }
        val total = rows.size
        val avg = if (total > 0) (1..5).sumOf { it * stars[it] }.toDouble() / total else 0.0
        summary += "Số đánh giá trong kỳ" to total.toString()
        summary += "Điểm trung bình" to if (total > 0) String.format(Locale.US, "%.2f / 5", avg) else "Chưa có"
        if (total > 0) summary += "Phân bố sao (5→1)" to (5 downTo 1).joinToString("  ") { "$it★: ${stars[it]}" }

        tables += ReportTable(
            title = "Đánh giá sản phẩm",
            sheetName = "DanhGia",
            columns = listOf(
                ReportColumn("Ngày", 1.1f), ReportColumn("Sản phẩm", 1.8f), ReportColumn("Khách hàng", 1.2f), ReportColumn("Đơn", 0.5f),
                ReportColumn("Sao", 0.5f), ReportColumn("Nhận xét", 2.2f), ReportColumn("Trạng thái", 0.8f)
            ),
            rows = rows,
            totals = if (total > 0) listOf(
                ReportCell.Text("Trung bình"), ReportCell.Text("$total đánh giá"), ReportCell.Text(""), ReportCell.Text(""),
                ReportCell.Decimal(avg), ReportCell.Text(""), ReportCell.Text("")
            ) else null
        )
    }
}
