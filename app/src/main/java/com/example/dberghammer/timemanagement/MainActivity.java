package com.example.dberghammer.timemanagement;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements Runnable{
    Socket socket;
    String ip="10.10.107.24";
    int port=1234;
    BufferedWriter bw;
    BufferedReader br;
    String gruppe="gruppe";
    Event e=null;

    SQLiteDatabase db;
    SQLiteDatabase dbr;
    Cursor cursor;
    private static SimpleCursorAdapter cursorAdapter;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DBHelper dbHelper = new DBHelper(this);
        db = dbHelper.getWritableDatabase();
        dbr=dbHelper.getReadableDatabase();
        readFromDatabase();

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build());


        try {
            socket=new Socket(ip, port);
            bw=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            br=new BufferedReader(new InputStreamReader(socket.getInputStream()));

          
        } catch (IOException e) {
            Log.e("----", "Fehler try");
            e.printStackTrace();
        }

    }

    private void readFromDatabase() {
        cursor = db.query(NotizenTable.TABLE_NAME, NotizenTable.ALL_COLUMS, null, null, null, null, null);
        cursorAdapter = new SimpleCursorAdapter(this, // Context
                android.R.layout.two_line_list_item, // Style der ListView
                cursor, // Cursor
                new String[] {NotizenTable.TITLE, NotizenTable.DATE}, // Spalten, die in der ListView angezeigt werden sollen
                new int[] {android.R.id.text1, android.R.id.text2}, // Textfelder, in die die Daten (Manufacturer und Model) kommen
                0); // Flag

        ListView terminListe = (ListView) findViewById(R.id.terminListe);
        registerForContextMenu(terminListe);
        terminListe.setAdapter(cursorAdapter);
        cursorAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.optionmenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.menuAdd:
                add();
                break;
            case R.id.menuGroupUpdate:
                refresh();
                break;
            default:
                return false;
        }
        return super.onOptionsItemSelected(item);
    }

    private void add() {
        Intent i=new Intent(this, addEventClass.class);
        startActivityForResult(i, 123);
        DBHelper dbHelper = new DBHelper(this);



    }

    private void refresh() {
        try {
            final EditText txt=new EditText(this);
            AlertDialog.Builder builder =new AlertDialog.Builder(this);
            builder.setMessage("Gruppe eingeben")
                    .setCancelable(false)
                    .setView(txt)
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            gruppe=txt.getText().toString();
                        }
                    });
            Thread t=new Thread(this);
            t.start();

            bw.write("refresh:gruppe \r\n");
            bw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    @Override
    protected void onStart() {
        try {
            socket=new Socket(ip, port);
            bw=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));



        } catch (IOException e) {
            Log.e("----", "Fehler try");
            e.printStackTrace();
        }
        super.onStart();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

       Bundle extras =data.getExtras();
        String name=extras.getString("name");
        Date date=(Date)data.getSerializableExtra("date");
        int tagev=extras.getInt("tagev");
        String note=extras.getString("note");
        e=new Event(note,name,date,tagev);
        ContentValues cv=new ContentValues();
        cv.put(NotizenTable.TITLE,e.getName());
        cv.put(NotizenTable.DATE,e.getD().toString());
        cv.put(NotizenTable.TIMEBEFORE,e.getTagev());
        cv.put(NotizenTable.NOTE,e.getNotiz());

        db.insert(NotizenTable.TABLE_NAME, null, cv);
        readFromDatabase();
        super.onActivityResult(requestCode, resultCode, data);

    }

    @Override
    protected void onPause() {
        try {
            socket.close();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onPause();
    }

    @Override
    public void run() {

        while (true) {

            try {
                Date d=null;
                String ev=br.readLine();
                String []events=ev.split(":");
                SimpleDateFormat sdf=new SimpleDateFormat("dd-MM-yyyy");
                try {
                     d= sdf.parse(events[2]);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                Event e=new Event(events[0],events[1], d ,Integer.parseInt(events[3]));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        int id = v.getId();
        if(id == R.id.terminListe) {
            getMenuInflater().inflate(R.menu.contextmenu, menu);
        }
        
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        int selectedPos;
        int id;
        info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        selectedPos = info.position;
        switch(item.getItemId()) {
            case R.id.delete:

                cursor = db.query(NotizenTable.TABLE_NAME, NotizenTable.ALL_COLUMS, null, null, null, null, null);
                cursor.move(selectedPos + 1);
                id = cursor.getInt(cursor.getColumnIndex("_id"));
                db.execSQL("DELETE FROM " + NotizenTable.TABLE_NAME + " WHERE _id = " + id);
                readFromDatabase();
                break;

            case R.id.release:
                try {

                    Cursor c= db.query(NotizenTable.TABLE_NAME,null,NotizenTable.id+" = "+selectedPos,null,null,null,null);
                    Log.v("+++++",cursor.getString(1)+", "+cursor.getString(2)+", "+Integer.parseInt(cursor.getString(3))+", "+cursor.getString(4));
                    e.setName(cursor.getString(1));
                    e.setD(new Date(cursor.getString(2)));
                    e.setTagev(Integer.parseInt(cursor.getString(3)));
                    e.setNotiz(cursor.getString(4));

                    final EditText txt=new EditText(this);
                    AlertDialog.Builder builder =new AlertDialog.Builder(this);
                    builder.setMessage("Gruppe eingeben")
                            .setCancelable(false)
                            .setView(txt)
                            .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                gruppe=txt.getText().toString();
                                }
                            });
                    builder.create();
                    bw.write("add:"+e.getName()+";"+e.getD()+";"+e.getTagev()+";"+e.getNotiz()+":"+gruppe+" \r\n");
                    bw.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                break;
            default:

        }

        return super.onContextItemSelected(item);

    }
}
