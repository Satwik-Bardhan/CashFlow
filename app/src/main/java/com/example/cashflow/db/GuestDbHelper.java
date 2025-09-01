package com.example.cashflow.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.cashflow.TransactionModel;

import java.util.ArrayList;
import java.util.List;

public class GuestDbHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "CashFlowGuest.db";

    // Transactions Table
    private static final String TABLE_TRANSACTIONS = "transactions";
    private static final String COLUMN_ID = "id"; // Local DB Primary Key
    private static final String COLUMN_TRANSACTION_ID = "transaction_id"; // Unique ID for each transaction
    private static final String COLUMN_TYPE = "type";
    private static final String COLUMN_AMOUNT = "amount";
    private static final String COLUMN_CATEGORY = "category";
    private static final String COLUMN_MODE = "mode";
    private static final String COLUMN_PARTY = "party";
    private static final String COLUMN_REMARK = "remark";
    private static final String COLUMN_TIMESTAMP = "timestamp";

    public GuestDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TRANSACTIONS_TABLE = "CREATE TABLE " + TABLE_TRANSACTIONS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_TRANSACTION_ID + " TEXT UNIQUE,"
                + COLUMN_TYPE + " TEXT,"
                + COLUMN_AMOUNT + " REAL,"
                + COLUMN_CATEGORY + " TEXT,"
                + COLUMN_MODE + " TEXT,"
                + COLUMN_PARTY + " TEXT,"
                + COLUMN_REMARK + " TEXT,"
                + COLUMN_TIMESTAMP + " INTEGER" + ")";
        db.execSQL(CREATE_TRANSACTIONS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSACTIONS);
        onCreate(db);
    }

    private ContentValues getContentValues(TransactionModel transaction) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_TRANSACTION_ID, transaction.getTransactionId());
        values.put(COLUMN_TYPE, transaction.getType());
        values.put(COLUMN_AMOUNT, transaction.getAmount());
        values.put(COLUMN_CATEGORY, transaction.getTransactionCategory());
        values.put(COLUMN_MODE, transaction.getPaymentMode());
        values.put(COLUMN_PARTY, transaction.getPartyName());
        values.put(COLUMN_REMARK, transaction.getRemark());
        values.put(COLUMN_TIMESTAMP, transaction.getTimestamp());
        return values;
    }

    public void addTransaction(TransactionModel transaction) {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            db.insert(TABLE_TRANSACTIONS, null, getContentValues(transaction));
        } catch (Exception e) {
            Log.e("GuestDbHelper", "Error adding transaction", e);
        }
    }

    public void updateTransaction(TransactionModel transaction) {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            db.update(TABLE_TRANSACTIONS, getContentValues(transaction),
                    COLUMN_TRANSACTION_ID + " = ?",
                    new String[]{String.valueOf(transaction.getTransactionId())});
        } catch (Exception e) {
            Log.e("GuestDbHelper", "Error updating transaction", e);
        }
    }

    public void deleteTransaction(String transactionId) {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            db.delete(TABLE_TRANSACTIONS, COLUMN_TRANSACTION_ID + " = ?",
                    new String[]{transactionId});
        } catch (Exception e) {
            Log.e("GuestDbHelper", "Error deleting transaction", e);
        }
    }

    public List<TransactionModel> getAllTransactions() {
        List<TransactionModel> transactionList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_TRANSACTIONS + " ORDER BY " + COLUMN_TIMESTAMP + " DESC";

        try (SQLiteDatabase db = this.getReadableDatabase();
             Cursor cursor = db.rawQuery(selectQuery, null)) {

            if (cursor.moveToFirst()) {
                do {
                    TransactionModel transaction = new TransactionModel();
                    transaction.setTransactionId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TRANSACTION_ID)));
                    transaction.setType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE)));
                    transaction.setAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_AMOUNT)));
                    transaction.setTransactionCategory(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)));
                    transaction.setPaymentMode(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MODE)));
                    transaction.setPartyName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PARTY)));
                    transaction.setRemark(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REMARK)));
                    transaction.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                    transactionList.add(transaction);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("GuestDbHelper", "Error getting all transactions", e);
        }
        return transactionList;
    }
}
