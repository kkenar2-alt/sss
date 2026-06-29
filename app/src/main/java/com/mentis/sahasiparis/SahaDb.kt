package com.mentis.sahasiparis

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.max

// Basit ama gerçek veri tutan Android SQLite çekirdeği.
// Üretim sürümünde bu katman DİA API ve merkez senkron servisi ile genişletilmelidir.
class SahaDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE customers(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT,
                address TEXT,
                lat REAL,
                lng REAL,
                notes TEXT,
                photo_count INTEGER DEFAULT 0,
                created_at TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE products(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                supplier TEXT,
                unit TEXT NOT NULL DEFAULT 'Adet'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE purchases(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                supplier TEXT,
                trx_date TEXT NOT NULL,
                qty REAL NOT NULL,
                unit_price REAL NOT NULL,
                total REAL NOT NULL,
                remaining_qty REAL NOT NULL,
                FOREIGN KEY(product_id) REFERENCES products(id)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE sales(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                trx_date TEXT NOT NULL,
                qty REAL NOT NULL,
                unit_price REAL NOT NULL,
                total REAL NOT NULL,
                lifo_cost REAL NOT NULL,
                profit REAL NOT NULL,
                FOREIGN KEY(customer_id) REFERENCES customers(id),
                FOREIGN KEY(product_id) REFERENCES products(id)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE expenses(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                trx_date TEXT NOT NULL,
                seller TEXT,
                description TEXT,
                qty REAL,
                total REAL NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE adjustments(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                trx_date TEXT NOT NULL,
                qty REAL NOT NULL,
                total REAL NOT NULL,
                type TEXT NOT NULL,
                note TEXT,
                FOREIGN KEY(product_id) REFERENCES products(id)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE contacts(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER NOT NULL,
                trx_date TEXT NOT NULL,
                contact_type TEXT NOT NULL,
                note TEXT,
                FOREIGN KEY(customer_id) REFERENCES customers(id)
            )
        """.trimIndent())

        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS contacts")
        db.execSQL("DROP TABLE IF EXISTS adjustments")
        db.execSQL("DROP TABLE IF EXISTS expenses")
        db.execSQL("DROP TABLE IF EXISTS sales")
        db.execSQL("DROP TABLE IF EXISTS purchases")
        db.execSQL("DROP TABLE IF EXISTS products")
        db.execSQL("DROP TABLE IF EXISTS customers")
        onCreate(db)
    }

    private fun seed(db: SQLiteDatabase) {
        val p1 = insertProduct(db, "STK001", "Su 0.5 LT", "Ana Tedarikçi", "Adet")
        val p2 = insertProduct(db, "STK002", "Ayran", "Süt Ürünleri", "Adet")
        val p3 = insertProduct(db, "STK003", "Sandviç", "Gıda Tedarik", "Adet")
        insertCustomer(db, "Örnek Market", "0555 111 22 33", "Merkez Mahallesi", "Numune cari", DateUtil.today())
        insertCustomer(db, "Kantin A", "0555 444 55 66", "Okul Caddesi", "Haftalık sipariş", DateUtil.today())
        insertPurchase(db, p1, "Ana Tedarikçi", DateUtil.today(), 100.0, 5.0)
        insertPurchase(db, p2, "Süt Ürünleri", DateUtil.today(), 80.0, 8.0)
        insertPurchase(db, p3, "Gıda Tedarik", DateUtil.today(), 40.0, 22.0)
    }

    fun addCustomer(name: String, phone: String, address: String, notes: String): Long {
        return insertCustomer(writableDatabase, name, phone, address, notes, DateUtil.today())
    }

    fun addProduct(code: String, name: String, supplier: String, unit: String): Long {
        return insertProduct(writableDatabase, code, name, supplier, unit)
    }

    fun addPurchase(productId: Long, supplier: String, date: String, qty: Double, unitPrice: Double): Long {
        return insertPurchase(writableDatabase, productId, supplier, date, qty, unitPrice)
    }

    fun addExpense(date: String, seller: String, description: String, qty: Double, total: Double): Long {
        val cv = ContentValues().apply {
            put("trx_date", date)
            put("seller", seller)
            put("description", description)
            put("qty", qty)
            put("total", total)
        }
        return writableDatabase.insert("expenses", null, cv)
    }

    fun addAdjustment(productId: Long, date: String, qty: Double, total: Double, type: String, note: String): Long {
        val cv = ContentValues().apply {
            put("product_id", productId)
            put("trx_date", date)
            put("qty", qty)
            put("total", total)
            put("type", type)
            put("note", note)
        }
        return writableDatabase.insert("adjustments", null, cv)
    }

    fun addContact(customerId: Long, date: String, type: String, note: String): Long {
        val cv = ContentValues().apply {
            put("customer_id", customerId)
            put("trx_date", date)
            put("contact_type", type)
            put("note", note)
        }
        return writableDatabase.insert("contacts", null, cv)
    }

    fun addSale(customerId: Long, productId: Long, date: String, qty: Double, unitPrice: Double): SaleResult {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val total = qty * unitPrice
            val cost = consumeLifo(db, productId, qty)
            val profit = total - cost
            val cv = ContentValues().apply {
                put("customer_id", customerId)
                put("product_id", productId)
                put("trx_date", date)
                put("qty", qty)
                put("unit_price", unitPrice)
                put("total", total)
                put("lifo_cost", cost)
                put("profit", profit)
            }
            val id = db.insert("sales", null, cv)
            db.setTransactionSuccessful()
            SaleResult(id, total, cost, profit)
        } finally {
            db.endTransaction()
        }
    }

    private fun consumeLifo(db: SQLiteDatabase, productId: Long, saleQty: Double): Double {
        var remainingToSell = saleQty
        var cost = 0.0
        val c = db.rawQuery(
            """
            SELECT id, remaining_qty, unit_price
            FROM purchases
            WHERE product_id = ? AND remaining_qty > 0
            ORDER BY trx_date DESC, id DESC
            """.trimIndent(),
            arrayOf(productId.toString())
        )
        c.use {
            while (it.moveToNext() && remainingToSell > 0.0001) {
                val purchaseId = it.getLong(0)
                val available = it.getDouble(1)
                val price = it.getDouble(2)
                val used = minOf(available, remainingToSell)
                cost += used * price
                remainingToSell -= used
                val newRemaining = available - used
                val cv = ContentValues().apply { put("remaining_qty", newRemaining) }
                db.update("purchases", cv, "id=?", arrayOf(purchaseId.toString()))
            }
        }
        // Yetersiz stokta kalan miktarı maliyetsiz bırakır; üretim sürümünde satış blokajı yapılmalı.
        return cost
    }

    fun saveCustomerLocation(customerId: Long, lat: Double, lng: Double) {
        val cv = ContentValues().apply {
            put("lat", lat)
            put("lng", lng)
        }
        writableDatabase.update("customers", cv, "id=?", arrayOf(customerId.toString()))
    }

    fun increaseCustomerPhoto(customerId: Long) {
        writableDatabase.execSQL("UPDATE customers SET photo_count = photo_count + 1 WHERE id = ?", arrayOf(customerId))
    }

    fun listCustomers(): List<Customer> {
        val out = mutableListOf<Customer>()
        val c = readableDatabase.rawQuery("SELECT id,name,phone,address,lat,lng,notes,photo_count FROM customers ORDER BY name", null)
        c.use {
            while (it.moveToNext()) {
                out.add(Customer(
                    it.getLong(0), it.getString(1), it.getString(2) ?: "", it.getString(3) ?: "",
                    if (it.isNull(4)) null else it.getDouble(4),
                    if (it.isNull(5)) null else it.getDouble(5),
                    it.getString(6) ?: "", it.getInt(7)
                ))
            }
        }
        return out
    }

    fun listProducts(): List<Product> {
        val out = mutableListOf<Product>()
        val c = readableDatabase.rawQuery("SELECT id,code,name,supplier,unit FROM products ORDER BY name", null)
        c.use {
            while (it.moveToNext()) {
                out.add(Product(it.getLong(0), it.getString(1), it.getString(2), it.getString(3) ?: "", it.getString(4)))
            }
        }
        return out
    }

    fun listSales(limit: Int = 50): List<SaleLine> {
        val out = mutableListOf<SaleLine>()
        val c = readableDatabase.rawQuery(
            """
            SELECT s.trx_date, c.name, p.name, s.qty, s.unit_price, s.total, s.lifo_cost, s.profit
            FROM sales s
            JOIN customers c ON c.id=s.customer_id
            JOIN products p ON p.id=s.product_id
            ORDER BY s.trx_date DESC, s.id DESC
            LIMIT ?
            """.trimIndent(), arrayOf(limit.toString()))
        c.use {
            while (it.moveToNext()) {
                out.add(SaleLine(it.getString(0), it.getString(1), it.getString(2), it.getDouble(3), it.getDouble(4), it.getDouble(5), it.getDouble(6), it.getDouble(7)))
            }
        }
        return out
    }

    fun inventoryReport(start: String, end: String): List<InventoryRow> {
        val rows = mutableListOf<InventoryRow>()
        for (p in listProducts()) {
            val beforeQty = sum("SELECT COALESCE(SUM(qty),0) FROM purchases WHERE product_id=? AND trx_date < ?", p.id, start) -
                    sum("SELECT COALESCE(SUM(qty),0) FROM sales WHERE product_id=? AND trx_date < ?", p.id, start) -
                    sum("SELECT COALESCE(SUM(qty),0) FROM adjustments WHERE product_id=? AND trx_date < ?", p.id, start)
            val beforeVal = sum("SELECT COALESCE(SUM(total),0) FROM purchases WHERE product_id=? AND trx_date < ?", p.id, start) -
                    sum("SELECT COALESCE(SUM(lifo_cost),0) FROM sales WHERE product_id=? AND trx_date < ?", p.id, start) -
                    sum("SELECT COALESCE(SUM(total),0) FROM adjustments WHERE product_id=? AND trx_date < ?", p.id, start)

            val buyQty = sum("SELECT COALESCE(SUM(qty),0) FROM purchases WHERE product_id=? AND trx_date BETWEEN ? AND ?", p.id, start, end)
            val buyVal = sum("SELECT COALESCE(SUM(total),0) FROM purchases WHERE product_id=? AND trx_date BETWEEN ? AND ?", p.id, start, end)
            val saleQty = sum("SELECT COALESCE(SUM(qty),0) FROM sales WHERE product_id=? AND trx_date BETWEEN ? AND ?", p.id, start, end)
            val saleVal = sum("SELECT COALESCE(SUM(total),0) FROM sales WHERE product_id=? AND trx_date BETWEEN ? AND ?", p.id, start, end)
            val saleCost = sum("SELECT COALESCE(SUM(lifo_cost),0) FROM sales WHERE product_id=? AND trx_date BETWEEN ? AND ?", p.id, start, end)
            val adjQty = sum("SELECT COALESCE(SUM(qty),0) FROM adjustments WHERE product_id=? AND trx_date BETWEEN ? AND ?", p.id, start, end)
            val adjVal = sum("SELECT COALESCE(SUM(total),0) FROM adjustments WHERE product_id=? AND trx_date BETWEEN ? AND ?", p.id, start, end)
            val remainQty = beforeQty + buyQty - saleQty - adjQty
            val remainVal = beforeVal + buyVal - saleCost - adjVal
            rows.add(InventoryRow(p.code, p.name, p.supplier, beforeQty, beforeVal, buyQty, buyVal, saleQty, saleVal, adjQty, adjVal, remainQty, remainVal, saleVal - saleCost))
        }
        return rows
    }

    fun expenseReport(start: String, end: String): List<ExpenseLine> {
        val out = mutableListOf<ExpenseLine>()
        val c = readableDatabase.rawQuery(
            "SELECT trx_date,seller,description,qty,total FROM expenses WHERE trx_date BETWEEN ? AND ? ORDER BY trx_date DESC, id DESC",
            arrayOf(start, end)
        )
        c.use {
            while (it.moveToNext()) out.add(ExpenseLine(it.getString(0), it.getString(1) ?: "", it.getString(2) ?: "", it.getDouble(3), it.getDouble(4)))
        }
        return out
    }

    private fun sum(sql: String, productId: Long, vararg dates: String): Double {
        val args = arrayOf(productId.toString(), *dates)
        readableDatabase.rawQuery(sql, args).use { c ->
            return if (c.moveToFirst()) max(0.0, c.getDouble(0)) else 0.0
        }
    }

    private fun insertCustomer(db: SQLiteDatabase, name: String, phone: String, address: String, notes: String, createdAt: String): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("phone", phone)
            put("address", address)
            put("notes", notes)
            put("created_at", createdAt)
        }
        return db.insert("customers", null, cv)
    }

    private fun insertProduct(db: SQLiteDatabase, code: String, name: String, supplier: String, unit: String): Long {
        val cv = ContentValues().apply {
            put("code", code)
            put("name", name)
            put("supplier", supplier)
            put("unit", unit)
        }
        return db.insertWithOnConflict("products", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun insertPurchase(db: SQLiteDatabase, productId: Long, supplier: String, date: String, qty: Double, unitPrice: Double): Long {
        val total = qty * unitPrice
        val cv = ContentValues().apply {
            put("product_id", productId)
            put("supplier", supplier)
            put("trx_date", date)
            put("qty", qty)
            put("unit_price", unitPrice)
            put("total", total)
            put("remaining_qty", qty)
        }
        return db.insert("purchases", null, cv)
    }

    companion object {
        private const val DB_NAME = "saha_siparis.db"
        private const val DB_VERSION = 1
    }
}

data class Customer(val id: Long, val name: String, val phone: String, val address: String, val lat: Double?, val lng: Double?, val notes: String, val photoCount: Int)
data class Product(val id: Long, val code: String, val name: String, val supplier: String, val unit: String)
data class SaleResult(val id: Long, val total: Double, val cost: Double, val profit: Double)
data class SaleLine(val date: String, val customer: String, val product: String, val qty: Double, val unitPrice: Double, val total: Double, val cost: Double, val profit: Double)
data class ExpenseLine(val date: String, val seller: String, val description: String, val qty: Double, val total: Double)
data class InventoryRow(
    val code: String,
    val name: String,
    val supplier: String,
    val startQty: Double,
    val startValue: Double,
    val buyQty: Double,
    val buyValue: Double,
    val saleQty: Double,
    val saleValue: Double,
    val adjustmentQty: Double,
    val adjustmentValue: Double,
    val remainQty: Double,
    val remainValue: Double,
    val profit: Double
)
