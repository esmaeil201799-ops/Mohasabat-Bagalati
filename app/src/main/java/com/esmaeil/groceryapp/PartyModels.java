package com.esmaeil.groceryapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "customers_suppliers")
public class CustomerSupplier {
    @PrimaryKey
    @NonNull
    public String entityId;
    public String name;
    public String phone;
    public String address;
    public String type; // CUSTOMER أو SUPPLIER
    public long balance; // الرصيد المستحق (Long)
}

@Entity(tableName = "invoice_allocations")
public class InvoiceAllocation {
    @PrimaryKey
    @NonNull
    public String allocationId;
    public String voucherId;
    public String invoiceId;
    public long allocatedAmount; // المبلغ المخصص للفاتورة (Long)
    public String date;
}
