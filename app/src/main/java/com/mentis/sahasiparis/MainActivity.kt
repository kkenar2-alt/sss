package com.mentis.sahasiparis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : Activity() {
    private lateinit var db: SahaDb

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = SahaDb(this)
        showHome()
    }

    private fun showHome() {
        val l = screen("Saha Sipariş Android")
        addInfo(l, "Cari, sipariş, dağıtım, alış, gider, fire/düzeltme, envanter ve LIFO kâr çekirdeği hazırdır. DİA, Google Maps ve OCR bağlantıları üretim aşamasında API anahtarı ile bağlanmalıdır.")
        addMenuButton(l, "Cari / Müşteri Kartları") { showCustomers() }
        addMenuButton(l, "Stok Kartları") { showProducts() }
        addMenuButton(l, "Sipariş / Satış Girişi") { showSale() }
        addMenuButton(l, "Dönem İçi Alış Girişi") { showPurchase() }
        addMenuButton(l, "Gider Girişi") { showExpense() }
        addMenuButton(l, "Fire / Düzeltme") { showAdjustment() }
        addMenuButton(l, "Satış Listesi") { showSales() }
        addMenuButton(l, "Dönemsel Envanter ve Kârlılık") { showInventoryReport(DateUtil.today(), DateUtil.today()) }
        addMenuButton(l, "Yapay Zekâ Rota Planı") { showRoutePlan() }
        addMenuButton(l, "DİA Entegrasyon Notları") { showDiaInfo() }
    }

    private fun showCustomers() {
        val l = screen("Cari / Müşteri Kartları")
        val name = input(l, "Cari Ünvanı", "")
        val phone = input(l, "Telefon", "")
        val address = input(l, "Adres", "")
        val notes = input(l, "Görüşme / Risk Notu", "")
        addPrimaryButton(l, "Cari Kaydet") {
            if (name.text.toString().trim().isEmpty()) {
                toast("Cari ünvanı boş olamaz")
            } else {
                db.addCustomer(name.text.toString(), phone.text.toString(), address.text.toString(), notes.text.toString())
                toast("Cari kaydedildi")
                showCustomers()
            }
        }

        val customers = db.listCustomers()
        if (customers.isNotEmpty()) {
            val sp = spinner(l, "Cari Seç", customers.map { "${it.name} - ${it.phone}" })
            addPrimaryButton(l, "Anlık Konumu Cariye Kaydet") {
                val customer = customers[sp.selectedItemPosition]
                saveLocationForCustomer(customer.id)
            }
            addPrimaryButton(l, "Müşteri Resmi Sayısını +1 Yap / Kamera Aç") {
                val customer = customers[sp.selectedItemPosition]
                db.increaseCustomerPhoto(customer.id)
                try { startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) } catch (_: Exception) {}
                toast("Resim kaydı eklendi. Üretim sürümünde dosya yolu da kaydedilir.")
                showCustomers()
            }
            addPrimaryButton(l, "Kartvizit / Cari Kart OCR") {
                toast("OCR için ML Kit Text Recognition bağlanacak. README içinde bağlantı notu var.")
                try { startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) } catch (_: Exception) {}
            }
            addPrimaryButton(l, "Haritada Aç") {
                val customer = customers[sp.selectedItemPosition]
                if (customer.lat == null || customer.lng == null) {
                    toast("Bu caride konum yok")
                } else {
                    val uri = Uri.parse("geo:${customer.lat},${customer.lng}?q=${customer.lat},${customer.lng}(${Uri.encode(customer.name)})")
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }

        addHeader(l, "Cari Listesi")
        val table = tableOf(listOf("Cari", "Tel", "Adres", "Konum", "Resim"))
        for (c in customers) {
            val loc = if (c.lat != null && c.lng != null) "${MoneyUtil.q(c.lat)}, ${MoneyUtil.q(c.lng)}" else "-"
            addRow(table, listOf(c.name, c.phone, c.address, loc, c.photoCount.toString()))
        }
        l.addView(table)
    }

    private fun showProducts() {
        val l = screen("Stok Kartları")
        val code = input(l, "Stok Kodu", "")
        val name = input(l, "Stok Adı", "")
        val supplier = input(l, "Tedarikçi", "")
        val unit = input(l, "Birim", "Adet")
        addPrimaryButton(l, "Stok Kaydet") {
            if (code.text.toString().isBlank() || name.text.toString().isBlank()) {
                toast("Stok kodu ve adı zorunlu")
            } else {
                db.addProduct(code.text.toString(), name.text.toString(), supplier.text.toString(), unit.text.toString())
                toast("Stok kaydedildi")
                showProducts()
            }
        }
        addHeader(l, "Stok Listesi")
        val table = tableOf(listOf("Kod", "Stok", "Tedarikçi", "Birim"))
        db.listProducts().forEach { addRow(table, listOf(it.code, it.name, it.supplier, it.unit)) }
        l.addView(table)
    }

    private fun showSale() {
        val l = screen("Sipariş / Satış Girişi")
        val customers = db.listCustomers()
        val products = db.listProducts()
        if (customers.isEmpty() || products.isEmpty()) {
            addInfo(l, "Önce cari ve stok kartı açılmalıdır.")
            return
        }
        val csp = spinner(l, "Cari", customers.map { it.name })
        val psp = spinner(l, "Stok", products.map { "${it.code} - ${it.name}" })
        val date = input(l, "Tarih", DateUtil.today())
        val qty = input(l, "Miktar", "1")
        val price = input(l, "Birim Satış Fiyatı", "0")
        val contactNote = input(l, "Görüşme Notu", "Sipariş alındı")
        addPrimaryButton(l, "Sipariş / Satış Kaydet") {
            val q = qty.d()
            val unitPrice = price.d()
            if (q <= 0 || unitPrice < 0) {
                toast("Miktar ve fiyat kontrol edilmeli")
            } else {
                val customer = customers[csp.selectedItemPosition]
                val product = products[psp.selectedItemPosition]
                val result = db.addSale(customer.id, product.id, date.text.toString(), q, unitPrice)
                db.addContact(customer.id, date.text.toString(), "Sipariş", contactNote.text.toString())
                toast("Satış: ${MoneyUtil.m(result.total)} / LIFO maliyet: ${MoneyUtil.m(result.cost)} / Kâr: ${MoneyUtil.m(result.profit)}")
                showSale()
            }
        }
    }

    private fun showPurchase() {
        val l = screen("Dönem İçi Alış Girişi")
        val products = db.listProducts()
        if (products.isEmpty()) {
            addInfo(l, "Önce stok kartı açılmalıdır.")
            return
        }
        val psp = spinner(l, "Stok", products.map { "${it.code} - ${it.name}" })
        val supplier = input(l, "Satıcı / Tedarikçi", products.first().supplier)
        val date = input(l, "Tarih", DateUtil.today())
        val qty = input(l, "Alış Miktarı", "1")
        val price = input(l, "Birim Alış Fiyatı", "0")
        addPrimaryButton(l, "Alış Kaydet") {
            val product = products[psp.selectedItemPosition]
            db.addPurchase(product.id, supplier.text.toString(), date.text.toString(), qty.d(), price.d())
            toast("Dönem içi alış kaydedildi")
            showPurchase()
        }
    }

    private fun showExpense() {
        val l = screen("Gider Girişi")
        val date = input(l, "Tarih", DateUtil.today())
        val seller = input(l, "Satıcı", "")
        val desc = input(l, "Gider Açıklaması", "Yakıt / yemek / araç / personel")
        val qty = input(l, "Miktar", "1")
        val total = input(l, "Tutar", "0")
        addPrimaryButton(l, "Gider Kaydet") {
            db.addExpense(date.text.toString(), seller.text.toString(), desc.text.toString(), qty.d(), total.d())
            toast("Gider kaydedildi")
            showExpense()
        }
        addHeader(l, "Bugünkü Giderler")
        val table = tableOf(listOf("Tarih", "Satıcı", "Açıklama", "Miktar", "Tutar"))
        db.expenseReport(DateUtil.today(), DateUtil.today()).forEach {
            addRow(table, listOf(it.date, it.seller, it.description, MoneyUtil.q(it.qty), MoneyUtil.m(it.total)))
        }
        l.addView(table)
    }

    private fun showAdjustment() {
        val l = screen("Fire / Düzeltme")
        val products = db.listProducts()
        if (products.isEmpty()) {
            addInfo(l, "Önce stok kartı açılmalıdır.")
            return
        }
        val psp = spinner(l, "Stok", products.map { "${it.code} - ${it.name}" })
        val date = input(l, "Tarih", DateUtil.today())
        val qty = input(l, "Düşülecek Miktar", "1")
        val total = input(l, "Düşülecek Tutar", "0")
        val type = input(l, "Tip", "FIRE")
        val note = input(l, "Not", "")
        addPrimaryButton(l, "Fire / Düzeltme Kaydet") {
            val product = products[psp.selectedItemPosition]
            db.addAdjustment(product.id, date.text.toString(), qty.d(), total.d(), type.text.toString(), note.text.toString())
            toast("Fire/düzeltme kaydedildi")
            showAdjustment()
        }
    }

    private fun showSales() {
        val l = screen("Satış Listesi")
        val table = tableOf(listOf("Tarih", "Cari", "Stok", "Miktar", "Fiyat", "Tutar", "Maliyet", "Kâr"))
        db.listSales().forEach {
            addRow(table, listOf(it.date, it.customer, it.product, MoneyUtil.q(it.qty), MoneyUtil.m(it.unitPrice), MoneyUtil.m(it.total), MoneyUtil.m(it.cost), MoneyUtil.m(it.profit)))
        }
        l.addView(table)
    }

    private fun showInventoryReport(defaultStart: String, defaultEnd: String) {
        val l = screen("Dönemsel Envanter ve Kârlılık")
        val start = input(l, "Başlangıç Tarihi", defaultStart)
        val end = input(l, "Bitiş Tarihi", defaultEnd)
        addPrimaryButton(l, "Rapor Getir") { showInventoryReport(start.text.toString(), end.text.toString()) }
        addInfo(l, "Formül: Dönem Başı + Dönem İçi Alışlar - Dönem İçi Satışlar - Fire/Düzeltme = Kalan. Maliyet: LIFO.")
        val table = tableOf(listOf("Kod", "Stok", "Tedarikçi", "D.Başı Miktar", "D.Başı Tutar", "Alış Miktar", "Alış Tutar", "Satış Miktar", "Satış Tutar", "Fire/Düz. Miktar", "Kalan Miktar", "Kalan Tutar", "Kâr"))
        db.inventoryReport(defaultStart, defaultEnd).forEach {
            addRow(table, listOf(it.code, it.name, it.supplier, MoneyUtil.q(it.startQty), MoneyUtil.m(it.startValue), MoneyUtil.q(it.buyQty), MoneyUtil.m(it.buyValue), MoneyUtil.q(it.saleQty), MoneyUtil.m(it.saleValue), MoneyUtil.q(it.adjustmentQty), MoneyUtil.q(it.remainQty), MoneyUtil.m(it.remainValue), MoneyUtil.m(it.profit)))
        }
        l.addView(table)
    }

    private fun showRoutePlan() {
        val l = screen("Yapay Zekâ Destekli Rota Planı")
        addInfo(l, "Bu sürümde konumu kayıtlı cariler basit mesafe sıralaması ile listelenir. Üretimde Google Route Optimization / Routes API ve sipariş öncelik puanı ile optimize edilir.")
        val located = db.listCustomers().filter { it.lat != null && it.lng != null }
        if (located.size < 2) {
            addInfo(l, "Rota için en az 2 cariye konum kaydedin.")
            return
        }
        val ordered = nearestNeighbor(located)
        val table = tableOf(listOf("Sıra", "Cari", "Adres", "Konum"))
        ordered.forEachIndexed { i, c ->
            addRow(table, listOf((i + 1).toString(), c.name, c.address, "${MoneyUtil.q(c.lat ?: 0.0)}, ${MoneyUtil.q(c.lng ?: 0.0)}"))
        }
        l.addView(table)
        addPrimaryButton(l, "Google Maps'te İlk Noktayı Aç") {
            val first = ordered.first()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${first.lat},${first.lng}?q=${first.lat},${first.lng}(${Uri.encode(first.name)})")))
        }
    }

    private fun showDiaInfo() {
        val l = screen("DİA Entegrasyon Notları")
        addInfo(l, "DİA verileri APK içine doğrudan kullanıcı/şifre yazılarak bağlanmamalıdır. Güvenli çözüm: Android -> Güvenli API Servisi -> DİA. DİA dönem başı stok ve dönem içi alışlar bu servis üzerinden senkronize edilmelidir.")
        addInfo(l, "Envanter raporu DİA satış hareketine bağlı değildir: dönem başı, dönem içi DİA alışları, Android satışları, fire/düzeltme ve kalan üzerinden çalışır.")
        addInfo(l, "OCR için ML Kit Text Recognition; harita ve rota için Google Maps SDK / Routes API; fotoğraf için cihaz kamera depolama modülü bağlanmalıdır.")
    }

    private fun saveLocationForCustomer(customerId: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            toast("Konum izni verildikten sonra tekrar basın")
            return
        }
        try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc == null) {
                toast("Cihazdan anlık konum alınamadı. GPS açık olmalı.")
            } else {
                db.saveCustomerLocation(customerId, loc.latitude, loc.longitude)
                toast("Konum kaydedildi: ${MoneyUtil.q(loc.latitude)}, ${MoneyUtil.q(loc.longitude)}")
                showCustomers()
            }
        } catch (ex: Exception) {
            toast("Konum alınamadı: ${ex.message}")
        }
    }

    private fun nearestNeighbor(customers: List<Customer>): List<Customer> {
        val remaining = customers.toMutableList()
        val result = mutableListOf<Customer>()
        var current = remaining.removeAt(0)
        result.add(current)
        while (remaining.isNotEmpty()) {
            val nearest = remaining.minBy { distance(current, it) }
            remaining.remove(nearest)
            result.add(nearest)
            current = nearest
        }
        return result
    }

    private fun distance(a: Customer, b: Customer): Double {
        val r = 6371.0
        val lat1 = Math.toRadians(a.lat ?: 0.0)
        val lat2 = Math.toRadians(b.lat ?: 0.0)
        val dLat = Math.toRadians((b.lat ?: 0.0) - (a.lat ?: 0.0))
        val dLng = Math.toRadians((b.lng ?: 0.0) - (a.lng ?: 0.0))
        val h = sin(dLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2.0)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun screen(title: String): LinearLayout {
        val scroll = ScrollView(this)
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
        }
        scroll.addView(l)
        setContentView(scroll)
        val titleView = TextView(this).apply {
            text = title
            textSize = 22f
            setTextColor(Color.rgb(33, 33, 33))
            setPadding(0, 8, 0, 12)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        l.addView(titleView)
        if (title != "Saha Sipariş Android") {
            addPrimaryButton(l, "Ana Menü") { showHome() }
        }
        return l
    }

    private fun input(parent: LinearLayout, label: String, default: String): EditText {
        val tv = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 10, 0, 2)
        }
        parent.addView(tv)
        val e = EditText(this).apply {
            setText(default)
            textSize = 16f
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        parent.addView(e)
        return e
    }

    private fun spinner(parent: LinearLayout, label: String, items: List<String>): Spinner {
        parent.addView(TextView(this).apply { text = label; textSize = 14f; setPadding(0, 10, 0, 2) })
        val s = Spinner(this)
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        parent.addView(s)
        return s
    }

    private fun addMenuButton(parent: LinearLayout, text: String, action: () -> Unit) = addPrimaryButton(parent, text, action)

    private fun addPrimaryButton(parent: LinearLayout, text: String, action: () -> Unit) {
        val b = Button(this).apply {
            this.text = text
            textSize = 15f
            setPadding(8, 8, 8, 8)
            setOnClickListener { action() }
        }
        parent.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 0) })
    }

    private fun addHeader(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(Color.rgb(55, 71, 79))
            setPadding(0, 18, 0, 6)
        })
    }

    private fun addInfo(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(4, 8, 4, 8)
        })
    }

    private fun tableOf(headers: List<String>): TableLayout {
        val t = TableLayout(this).apply {
            isStretchAllColumns = false
            setPadding(0, 6, 0, 20)
        }
        addRow(t, headers, true)
        return t
    }

    private fun addRow(table: TableLayout, values: List<String>, header: Boolean = false) {
        val row = TableRow(this)
        values.forEach {
            row.addView(TextView(this).apply {
                text = it
                textSize = if (header) 13f else 12f
                setPadding(10, 8, 10, 8)
                setTextColor(if (header) Color.WHITE else Color.BLACK)
                setBackgroundColor(if (header) Color.rgb(69, 90, 100) else Color.rgb(245, 245, 245))
            })
        }
        table.addView(row)
    }

    private fun EditText.d(): Double {
        val raw = text.toString().trim()
        val normalized = if (raw.contains(",")) raw.replace(".", "").replace(",", ".") else raw
        return normalized.toDoubleOrNull() ?: 0.0
    }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
