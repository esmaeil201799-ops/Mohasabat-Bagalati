# Mohasabat-Bagalati

Official application name: **محاسبة بقالتي**. Copyright (c) 2026 إسماعيل أبكر.
See [COPYRIGHT](./COPYRIGHT) for the project ownership notice. The contact email
is intentionally unset. Third-party dependencies remain subject to their own
licenses; review them before commercial distribution. This notice is not legal advice.

The Android screens use Room for product, partner, inventory-opening, and sales
records. The old HTML asset remains separate and retains its LocalStorage data;
export its JSON backup and import it in the app to archive records without
overwriting or deleting the source. PIN authentication and role checks are
implemented in the service layer, and Room services now cover purchase,
payment, expense, withdrawal, and return posting. Several of these services are
not yet connected to operational screens. This remains an incomplete accounting
application; Room backup/restore and cloud sync are not implemented. See
[the Arabic engineering audit](./ENGINEERING_AUDIT_AR.md).