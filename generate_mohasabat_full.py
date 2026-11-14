import os
import sys
import shutil
from datetime import datetime

BASE_DIR = "app/src/main/java/com/esmaeil/groceryapp"
BACKUP_DIR = f"backup_code_{datetime.now().strftime('%Y%m%d_%H%M%S')}"

def verify_and_backup():
    """التحقق من جذر المشروع وعمل نسخة احتياطية للملفات القديمة إن وجدت حمايةً لعملك"""
    if not os.path.exists("settings.gradle") or not os.path.exists("app"):
        print("❌ خطأ فادح: أنت لست في جذر مشروع Android الصحيح! ضع السكربت بجانب settings.gradle.")
        sys.exit(1)
    
    if os.path.exists(BASE_DIR):
        print(f"📦 جاري إنشاء نسخة احتياطية للملفات الحالية في الدลيل: {BACKUP_DIR}")
        shutil.copytree(BASE_DIR, BACKUP_DIR)
        print("✅ تم عمل النسخة الاحتياطية بنجاح.")
    os.makedirs(BASE_DIR, exist_ok=True)

def write_file(filename, content):
    path = os.path.join(BASE_DIR, filename)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content.strip())
    print(f"✅ تم توليد الملف بنجاح: {filename}")

# 1. Account.java (دليل الحسابات المتقدم للقيد المزدوج)
account_code = """
package com.esmaeil.groceryapp;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "accounts", indices = {@Index(value = {"code"}, unique = true)})
public class Account {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String code;         // رمز الحساب (مثلاً: 1101 للصندوق)
    private String name;         // اسم الحساب
    private long parentId;       // الحساب الأب (0 إن لم يكن له أب)
    private int level;           // مستوى الحساب في الشجرة
    private int type;            // 1: أصول، 2: خصوم، 3: إيرادات، 4: مصروفات، 5: حقوق ملكية
    private boolean isGroup;     // هل هو حساب تجمعي (لا يقبل الحركة المباشرة)؟
    private boolean isActive;    // حالة الحساب

    public Account(String code, String name, long parentId, int level, int type, boolean isGroup, boolean isActive) {
        this.code = code;
        this.name = name;
        this.parentId = parentId;
        this.level = level;
        this.type = type;
        this.isGroup = isGroup;
        this.isActive = isActive;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public long getParentId() { return parentId; }
    public int getLevel() { return level; }
    public int getType() { return type; }
    public boolean isGroup() { return isGroup; }
    public boolean isActive() { return isActive; }
}
"""

# 2. Currency.java (العملات)
currency_code = """
package com.esmaeil.groceryapp;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "currencies", indices = {@Index(value = {"code"}, unique = true)})
public class Currency {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String code; // مثل: YER, SAR
    private String name;
    private boolean isBaseCurrency;

    public Currency(String code, String name, boolean isBaseCurrency) {
        this.code = code;
        this.name = name;
        this.isBaseCurrency = isBaseCurrency;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isBaseCurrency() { return isBaseCurrency; }
}
"""

# 3. ExchangeRate.java (أسعار الصرف التاريخية لحماية الفواتير)
exchange_rate_code = """
package com.esmaeil.groceryapp;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "exchange_rates",
        foreignKeys = @ForeignKey(entity = Currency.class, parentColumns = "id", childColumns = "currencyId", onDelete = ForeignKey.RESTRICT),
        indices = {@Index("currencyId"), @Index("effectiveDate")})
public class ExchangeRate {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long currencyId;
    private long rateAgainstBase; // سعر الصرف مقارنة بالعملة الأساسية (مخزن كعدد صحيح بدقة عالية)
    private long effectiveDate;   // تاريخ ووقت اعتماد سعر الصرف (Timestamp)

    public ExchangeRate(long currencyId, long rateAgainstBase, long effectiveDate) {
        this.currencyId = currencyId;
        this.rateAgainstBase = rateAgainstBase;
        this.effectiveDate = effectiveDate;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getCurrencyId() { return currencyId; }
    public long getRateAgainstBase() { return rateAgainstBase; }
    public long getEffectiveDate() { return effectiveDate; }
}
"""

# 4. Category.java & Unit.java & ProductUnit.java & Product.java
category_code = """
package com.esmaeil.groceryapp;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories", indices = {@Index(value = {"name"}, unique = true)})
public class Category {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;

    public Category(String name) { this.name = name; }
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
}
"""

unit_code = """
package com.esmaeil.groceryapp;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "units", indices = {@Index(value = {"name"}, unique = true)})
public class Unit {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name; // كيلو، جرام، حبة، سلة

    public Unit(String name) { this.name = name; }
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
}
"""

product_code = """
package com.esmaeil.groceryapp;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "products",
        foreignKeys = {
            @ForeignKey(entity = Category.class, parentColumns = "id", childColumns = "categoryId", onDelete = ForeignKey.RESTRICT),
            @ForeignKey(entity = Unit.class, parentColumns = "id", childColumns = "baseUnitId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {@Index("categoryId"), @Index("baseUnitId")})
public class Product {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;
    private long categoryId;
    private long baseUnitId;    // أصغر وحدة تخزين (مثل: جرام أو حبة)
    private long currentCost;   // متوسط التكلفة المرجح (قيمة مساعدة سريعة)

    public Product(String name, long categoryId, long baseUnitId, long currentCost) {
        this.name = name;
        this.categoryId = categoryId;
        this.baseUnitId = baseUnitId;
        this.currentCost = currentCost;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public long getCategoryId() { return categoryId; }
    public long getBaseUnitId() { return baseUnitId; }
    public long getCurrentCost() { return currentCost; }
    public void setCurrentCost(long currentCost) { this.currentCost = currentCost; }
}
"""

product_unit_code = """
package com.esmaeil.groceryapp;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "product_units",
        foreignKeys = {
            @ForeignKey(entity = Product.class, parentColumns = "id", childColumns = "productId", onDelete = ForeignKey.CASCADE),
            @ForeignKey(entity = Unit.class, parentColumns = "id", childColumns = "unitId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {@Index("productId"), @Index("unitId")})
public class ProductUnit {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long productId;
    private long unitId;
    private long conversionFactor; // معامل التحويل للوحدة الأساسية (مثلاً الكرتون = 12 حبة، أو سلة طماطم بوزن متغير تقريبي)
    private long defaultSalePrice;

    public ProductUnit(long productId, long unitId, long conversionFactor, long defaultSalePrice) {
        this.productId = productId;
        this.unitId = unitId;
        this.conversionFactor = conversionFactor;
        this.defaultSalePrice = defaultSalePrice;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getProductId() { return productId; }
    public long getUnitId() { return unitId; }
    public long getConversionFactor() { return conversionFactor; }
    public long getDefaultSalePrice() { return defaultSalePrice; }
}
"""

# 5. Customer.java & Supplier.java (مرتبطين بدليل الحسابات بـ Foreign Key)
customer_code = """
package com.esmaeil.groceryapp;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "customers",
        foreignKeys = @ForeignKey(entity = Account.class, parentColumns = "id", childColumns = "accountId", onDelete = ForeignKey.RESTRICT),
        indices = {@Index("accountId")})
public class Customer {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;
    private long accountId; // ربط صريح بدليل الحسابات لاستخراج الرصيد من القيود
    private String phone;

    public Customer(String name, long accountId, String phone) {
        this.name = name;
        this.accountId = accountId;
        this.phone = phone;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public long getAccountId() { return accountId; }
    public String getPhone() { return phone; }
}
"""

supplier_code = """
package com.esmaeil.groceryapp;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "suppliers",
        foreignKeys = @ForeignKey(entity = Account.class, parentColumns = "id", childColumns = "accountId", onDelete = ForeignKey.RESTRICT),
        indices = {@Index("accountId")})
public class Supplier {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;
    private long accountId;
    private String phone;

    public Supplier(String name, long accountId, String phone) {
        this.name = name;
        this.accountId = accountId;
        this.phone = phone;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public long getAccountId() { return accountId; }
    public String getPhone() { return phone; }
}
"""

# 6. StockMovement.java (سجل حركات المخزون - مصدر الحقيقة للمخزون)
stock_movement_code = """
package com.esmaeil.groceryapp;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "stock_movements",
        foreignKeys = {
            @ForeignKey(entity = Product.class, parentColumns = "id", childColumns = "productId", onDelete = ForeignKey.RESTRICT),
            @ForeignKey(entity = Unit.class, parentColumns = "id", childColumns = "unitId", onDelete = ForeignKey.RESTRICT)
        },
        indices = {@Index("productId"), @Index("unitId"), @Index("timestamp")})
public class StockMovement {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long productId;
    private long unitId;
    private long quantity;       // الكمية بالحركة (موجب للإدخال، سالب للإخراج)
    private long baseUnitQuantity; // الكمية محسوبة بأصغر وحدة تخزين لتوحيد الحسابات
    private long totalCost;      // إجمالي التكلفة متضمنة النصاريف والنقل الموزع
    private int movementType;    // 1: شراء، 2: بيع، 3: مرتجع شراء، 4: مرتجع بيع، 5: تسوية تالف
    private long referenceId;    // رقم الفاتورة أو المستند المرتبط
    private long timestamp;      // وقت الحركة

    public StockMovement(long productId, long unitId, long quantity, long baseUnitQuantity, long totalCost, int movementType, long referenceId, long timestamp) {
        this.productId = productId;
        this.unitId = unitId;
        this.quantity = quantity;
        this.baseUnitQuantity = baseUnitQuantity;
        this.totalCost = totalCost;
        this.movementType = movementType;
        this.referenceId = referenceId;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getProductId() { return productId; }
    public long getUnitId() { return unitId; }
    public long getQuantity() { return quantity; }
    public long getBaseUnitQuantity() { return baseUnitQuantity; }
    public long getTotalCost() { return totalCost; }
    public int getMovementType() { return movementType; }
    public long getReferenceId() { return referenceId; }
    public long getTimestamp() { return timestamp; }
}
"""

# ==================== DAOs ====================
product_dao_code = """
package com.esmaeil.groceryapp;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;
import java.util.List;

@Dao
public interface ProductDao {
    @Insert
    long insert(Product product);

    @Update
    void update(Product product);

    @Delete
    void delete(Product product);

    @Query("SELECT * FROM products")
    List<Product> getAllProducts();

    @Query("SELECT * FROM products WHERE id = :id")
    Product getProductById(long id);

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%'")
    List<Product> searchProducts(String query);
}
"""

customer_dao_code = """
package com.esmaeil.groceryapp;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface CustomerDao {
    @Insert
    long insert(Customer customer);

    @Update
    void update(Customer customer);

    @Query("SELECT * FROM customers")
    List<Customer> getAllCustomers();

    @Query("SELECT * FROM customers WHERE id = :id")
    Customer getCustomerById(long id);
}
"""

supplier_dao_code = """
package com.esmaeil.groceryapp;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface SupplierDao {
    @Insert
    long insert(Supplier supplier);

    @Update
    void update(Supplier supplier);

    @Query("SELECT * FROM suppliers")
    List<Supplier> getAllSuppliers();
}
"""

stock_movement_dao_code = """
package com.esmaeil.groceryapp;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface StockMovementDao {
    @Insert
    long insert(StockMovement movement);

    @Query("SELECT SUM(baseUnitQuantity) FROM stock_movements WHERE productId = :productId")
    long getProductStockBalance(long productId);

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY timestamp DESC")
    List<StockMovement> getMovementsForProduct(long productId);
}
"""

account_dao_code = """
package com.esmaeil.groceryapp;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface AccountDao {
    @Insert
    long insert(Account account);

    @Query("SELECT * FROM accounts")
    List<Account> getAllAccounts();

    @Query("SELECT * FROM accounts WHERE id = :id")
    Account getAccountById(long id);
}
"""

# 7. AppDatabase.java (قاعدة البيانات الموحدة المتكاملة مع جميع الجداول والـ DAOs)
app_database_code = """
package com.esmaeil.groceryapp;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
    entities = {
        Account.class,
        Currency.class,
        ExchangeRate.class,
        Category.class,
        Unit.class,
        Product.class,
        ProductUnit.class,
        Customer.class,
        Supplier.class,
        StockMovement.class
    },
    version = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract AccountDao accountDao();
    public abstract ProductDao productDao();
    public abstract CustomerDao customerDao();
    public abstract SupplierDao supplierDao();
    public abstract StockMovementDao stockMovementDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "grocery_pos.db"
                    )
                    // الحفاظ التام على أمان البيانات وعدم التدمير التلقائي
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
"""

if __name__ == "__main__":
    verify_and_backup()
    print("🚀 بدء توليد البنية الهندسية الكبرى المتكاملة لنظام محاسبة بقالتي 2.0...")
    
    write_file("Account.java", account_code)
    write_file("Currency.java", currency_code)
    write_file("ExchangeRate.java", exchange_rate_code)
    write_file("Category.java", category_code)
    write_file("Unit.java", unit_code)
    write_file("Product.java", product_code)
    write_file("ProductUnit.java", product_unit_code)
    write_file("Customer.java", customer_code)
    write_file("Supplier.java", supplier_code)
    write_file("StockMovement.java", stock_movement_code)
    write_file("ProductDao.java", product_dao_code)
    write_file("CustomerDao.java", customer_dao_code)
    write_file("SupplierDao.java", supplier_dao_code)
    write_file("StockMovementDao.java", stock_movement_dao_code)
    write_file("AccountDao.java", account_dao_code)
    write_file("AppDatabase.java", app_database_code)
    
    print("✨ تمت عملية توليد الحزمة الكبرى الكاملة بنجاح هندسي تام وآمن تماماً!")
