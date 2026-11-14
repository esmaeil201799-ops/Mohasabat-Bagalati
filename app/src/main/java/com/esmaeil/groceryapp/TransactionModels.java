package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "purchase_invoices")
public class PurchaseInvoice {
    @PrimaryKey
    @NonNull
    public String invoiceId;
    public String supplierId;
    public String date;
    public long totalAmount;
    public long transportCost; // مصاريف النقل والمصاريف المباشرة
    public boolean isPosted;
}

@Entity(tableName = "purchase_items")
public class PurchaseItem {
    @PrimaryKey
    @NonNull
    public String id;
    public String invoiceId;
    public String itemId;
    public long quantity;
    public String unitId;
    public long unitPrice;
}

@Entity(tableName = "sales_invoices")
public class SalesInvoice {
    @PrimaryKey
    @NonNull
    public String invoiceId;
    public String customerId;
    public String date;
    public long totalAmount;
    public long discount;
    public boolean isPosted;
}

@Entity(tableName = "sales_items")
public class SalesItem {
    @PrimaryKey
    @NonNull
    public String id;
    public String invoiceId;
    public String itemId;
    public long quantity;
    public String unitId;
    public long sellingPrice;
}

@Entity(tableName = "returns")
public class Return {
    @PrimaryKey
    @NonNull
    public String returnId;
    public String originalInvoiceId;
    public String returnType; // SALES_RETURN أو PURCHASE_RETURN
    public String date;
    public long totalAmount;
    public boolean isPosted;
}

@Entity(tableName = "return_items")
public class ReturnItem {
    @PrimaryKey
    @NonNull
    public String id;
    public String returnId;
    public String itemId;
    public long quantity;
    public String unitId;
    public long price;
}

@Entity(tableName = "receipt_vouchers")
public class ReceiptVoucher {
    @PrimaryKey
    @NonNull
    public String voucherId;
    public String customerId;
    public String boxId;
    public long amount;
    public String date;
    public String notes;
}

@Entity(tableName = "payment_vouchers")
public class PaymentVoucher {
    @PrimaryKey
    @NonNull
    public String voucherId;
    public String supplierId;
    public String boxId;
    public long amount;
    public String date;
    public String notes;
}
