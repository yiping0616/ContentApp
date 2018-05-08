package com.example.mom.contentapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;    // 後面code , 可直接Contacts.xxx , 不用 ContactsContract.Contacts.xxx
import android.provider.ContactsContract.CommonDataKinds.Phone;   // 後面code , 可直接Phone.xxx , 不用 ContactsContract.CommonDataKinds.Phone.xxx
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import static android.Manifest.permission.*;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CONTACTS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        insertContact();//新增聯絡人

        //權限確認 聯絡人權限 Contacts(聯絡人)
        int permission = ActivityCompat.checkSelfPermission(this, READ_CONTACTS);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            //未取得權限，向使用者要求允許權限
            ActivityCompat.requestPermissions( this,
                    new String[]{READ_CONTACTS, WRITE_CONTACTS},
                    REQUEST_CONTACTS );
        }else{
            //已有權限，可進行檔案存取
            readContacts();
        }
    }

    //向使用者要求權限後 , 不管APPLY OR DENY 都會執行 MainActivity 的 onRequestPermissionsResult()
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CONTACTS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //取得聯絡人權限 , 進行存取
                    readContacts();
                } else {
                    //使用者拒絕權限 , 顯示對話框告知
                    new AlertDialog.Builder(this)
                            .setMessage("必須允許聯絡人權限才能顯示資料")
                            .setPositiveButton("OK", null)
                            .show();
                }
                return;
        }
    }
    //讀取聯絡人資料 readContacts()
    private void readContacts() {
        //先取得 Contentresolver物件
        ContentResolver resolver = getContentResolver();

        //查詢 所有聯絡人資料 名字與電話 (包含沒有輸入電話的聯絡人)
        Cursor cursor = resolver.query(
                Contacts.CONTENT_URI ,
                null, null , null , null );  //projection:回傳的欄位 , select查詢的條件 ,sortorder: ASC|DESC

        //cursor初始指向 第一筆資料之前,並未指向任何一筆資料 , 呼叫 cursor.moveToNext() 將 cursor向下移動 , 有資料回傳true , 無資料回傳false
        //此處的while()只適合用於開發除錯用 在Log中顯示
        /*while (cursor.moveToNext()){
            //處理每一筆資料
            //ColumIndex 該欄位在查詢結果中的索引值
            int id = cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts._ID));
            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            Log.d("RECORD" , id +"/" +name);

        }*/

        //ListView的資料來源是 查詢結果Cursor物件 , 因此使用 SimpleCursorAdapter , 將資料庫查詢的資料放入ListView
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this ,
                android.R.layout.simple_list_item_2,      //兩列版面配置
                cursor,
                new String[] {Contacts.DISPLAY_NAME  , Contacts.HAS_PHONE_NUMBER} ,   //資料來源欄位 陣列
                new int[] {android.R.id.text1 , android.R.id.text2} ,                 //與其對應的 顯示元件 陣列
                1 ){
                //客製化SimpleCursorAdapter 利用 Override覆寫 bindView()
                //當每一列要顯示資料時，會自動呼叫 SimpleCursorAdapter 內的 newView()，以取得單列資料的View
                //bindView() 則會在每列的 View 元件要取得每個欄位內容時會自動被呼叫
                @Override
                public void bindView(View view, Context context, Cursor cursor) {
                    super.bindView(view, context, cursor);

                    TextView phone = view.findViewById(android.R.id.text2); //取得列中第二個Text
                    //若沒有Number
                    if (cursor.getInt( cursor.getColumnIndex( ContactsContract.Contacts.HAS_PHONE_NUMBER ) ) ==0){
                        phone.setText("");   //TextView 的 setText()
                    }
                    else{ //有Number
                        int id = cursor.getInt( cursor.getColumnIndex( Contacts._ID));  //取得聯絡人ID
                        //進行第二次查詢 , 查詢電話號碼表格
                        Cursor pCursor = getContentResolver().query(
                                Phone.CONTENT_URI ,
                                null ,
                                Phone.CONTACT_ID +"=?" ,           //查詢條件：Phone.CONTACT_ID = 取得的聯絡人ID
                                new String[]{String.valueOf(id)} ,
                                null );
                        if (pCursor.moveToFirst()){             //先把第二次查詢的pCursor往下移一筆  若有資料則放置text中
                            String number = pCursor.getString( pCursor.getColumnIndex( Phone.DATA));
                            phone.setText(number);
                        }
                    }
                }
            };

        ListView list = findViewById(R.id.list);
        list.setAdapter(adapter);
    }
    //新增聯絡人 ContentProviderOperation 所提供的方法  newInsert()  , newUpdate() , newDelete()
    //最後完成時 呼叫build() 即可產生 ContentProviderOperation物件
    private void insertContact(){
        ArrayList ops = new ArrayList();  //操作集合 , 存放content provider操作指令
        int index = ops.size();
        ops.add(ContentProviderOperation             //建立一個新增資料操作，並加到操作集合(ops)中，資料對象是 RawContacts，新增成功後本操作會得到其 ID 值
                .newInsert( RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE , null)
                .withValue(RawContacts.ACCOUNT_NAME , null).build()  );
        ops.add( ContentProviderOperation          //建立一個新增資料操作，並加到操作集合中，資料對象是 ContactsContract.Data，取得上一個新增至RawContacts記錄的ID值
                .newInsert(Data.CONTENT_URI)        //此段主要是寫入聯絡人的姓名
                .withValueBackReference(Data.RAW_CONTACT_ID , index)
                .withValue(Data.MIMETYPE , StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME , "Jane").build()  );
        ops.add( ContentProviderOperation           //建立新增到 Phone 的電話號碼操作，使用到第一個新增 RawContacts 操作後得到的 ID 值
                .newInsert(Data.CONTENT_URI)        //此段主要是瀉入聯絡人的電話號碼
                .withValueBackReference(Data.RAW_CONTACT_ID , index)
                .withValue(Data.MIMETYPE , Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER ,"0910000000" )
                .withValue(Phone.TYPE , Phone.TYPE_MOBILE).build()  );
        try{
            getContentResolver().applyBatch( ContactsContract.AUTHORITY , ops);     //批次執行操作集合
        }
        catch (RemoteException e){
            e.printStackTrace();
        }
        catch (OperationApplicationException e){
            e.printStackTrace();
        }
    }
    //更新 updateContact()  更新Jane的電話
    private void updateContact(){
        String where = Phone.DISPLAY_NAME + "=? AND" + Data.MIMETYPE +"=?" ;   //WHERE 的條件敘述 , 名稱=? AND 資料格式=?
        String[] params = new String[] {"Jane", Phone.CONTENT_ITEM_TYPE};    //對應到條件敘述中的資料值
        ArrayList ops = new ArrayList();
        ops.add(  ContentProviderOperation.newUpdate(Data.CONTENT_URI)    //建立一個更新操作 , 並加到操作集合(ops)中
                .withSelection(where , params)
                .withValue(Phone.NUMBER , "0900333333").build()  );
        try{
            getContentResolver().applyBatch(ContactsContract.AUTHORITY , ops);    //批次執行操作集合
        }
        catch (RemoteException e){
            e.printStackTrace();
        }
        catch (OperationApplicationException e ){
            e.printStackTrace();
        }
    }
    //刪除 deleteContact()    刪除 聯絡人中文姓名為 Jane的資料
    private void deletContact(){
        String where = Data.DISPLAY_NAME + "=?";    //WHERE條件敘述 名稱=?
        String[] params = new String[] {"Jane"};    //對應條件敘述中的資料值
        ArrayList ops = new ArrayList();
        ops.add(  ContentProviderOperation.newDelete( RawContacts.CONTENT_URI)    //建立一個刪除操作 , 並加到操作集合中
                .withSelection(where , params).build()  );
        try{
            getContentResolver().applyBatch(ContactsContract.AUTHORITY , ops);    //批次執行操作集合
        }
        catch (RemoteException e ){
            e.printStackTrace();
        }
        catch (OperationApplicationException e ){
            e.printStackTrace();
        }
    }
}