package com.example.playlist;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private DBHelper dbHelper;
    private SQLiteDatabase db;
    private SimpleCursorAdapter adapter;

    private TextView tvCount, tvTotal, tvLongest, tvFilterInfo;
    private View filterBar;

    // Текущий фильтр (null / пусто = условие не используется)
    private Integer durFrom, durTo;   // длительность, секунды
    private Integer yearFrom, yearTo; // год
    private String textQuery;         // часть имени исполнителя или названия

    // Текущее условие WHERE и аргументы (buildWhere)
    private String where;
    private String[] whereArgs;

    // Сортировка
    private static final String[] SORT_LABELS = {
            "по добавлению",
            "название А → Я",
            "название Я → А",
            "исполнитель А → Я",
            "год ↑ (старые сначала)",
            "год ↓ (новые сначала)",
            "длительность ↑",
            "длительность ↓"
    };
    private static final String[] SORT_ORDERS = {
            DBHelper.COL_ID + " ASC",
            DBHelper.COL_TITLE + " ASC",
            DBHelper.COL_TITLE + " DESC",
            DBHelper.COL_ARTIST + " ASC, " + DBHelper.COL_TITLE + " ASC",
            DBHelper.COL_YEAR + " ASC, " + DBHelper.COL_ID + " ASC",
            DBHelper.COL_YEAR + " DESC, " + DBHelper.COL_ID + " ASC",
            DBHelper.COL_DURATION + " ASC",
            DBHelper.COL_DURATION + " DESC"
    };
    private String orderBy = SORT_ORDERS[0];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        tvCount = findViewById(R.id.tvCount);
        tvTotal = findViewById(R.id.tvTotal);
        tvLongest = findViewById(R.id.tvLongest);
        tvFilterInfo = findViewById(R.id.tvFilterInfo);
        filterBar = findViewById(R.id.filterBar);

        // Гласморфизм
        GlassLayout glassHeader = findViewById(R.id.glassHeader);
        glassHeader.setBlurTarget(findViewById(R.id.imgHeader));

        dbHelper = new DBHelper(this);
        db = dbHelper.getWritableDatabase();

        setupList();
        setupSort();

        Button btnAdd = findViewById(R.id.btnAdd);
        Button btnFilter = findViewById(R.id.btnFilter);
        btnAdd.setOnClickListener(v -> showAddDialog());
        btnFilter.setOnClickListener(v -> showFilterDialog());
        findViewById(R.id.btnReset).setOnClickListener(v -> {
            clearFilter();
            refresh();
        });

        refresh();
    }

    // Список
    private void setupList() {
        String[] from = {DBHelper.COL_ARTIST, DBHelper.COL_TITLE, DBHelper.COL_YEAR, DBHelper.COL_DURATION};
        int[] to = {R.id.tvArtist, R.id.tvTitle, R.id.tvYear, R.id.tvDuration};

        adapter = new SimpleCursorAdapter(this, R.layout.item_track, null, from, to, 0) {
            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                super.bindView(view, context, cursor);
                // Порядковый номер в текущем списке: 01, 02, 03...
                TextView tvNum = view.findViewById(R.id.tvNum);
                tvNum.setText(String.format(Locale.US, "%02d", cursor.getPosition() + 1));
            }
        };

        // Длительность хранится в секундах, а показываем как 3:45
        adapter.setViewBinder((view, cursor, columnIndex) -> {
            if (columnIndex == cursor.getColumnIndex(DBHelper.COL_DURATION)) {
                ((TextView) view).setText(TimeUtils.format(cursor.getInt(columnIndex)));
                return true;
            }
            return false; // остальные поля SimpleCursorAdapter заполнит сам
        });

        ListView list = findViewById(R.id.listTracks);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.emptyView));

        // Долгое нажатие - удалить трек
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            TextView tvTitle = view.findViewById(R.id.tvTitle);
            new MaterialAlertDialogBuilder(this)
                    .setBackground(ContextCompat.getDrawable(this, R.drawable.dialog_bg))
                    .setTitle("Удалить трек?")
                    .setMessage("«" + tvTitle.getText() + "» будет удалён из плейлиста.")
                    .setPositiveButton("Удалить", (d, w) -> {
                        db.delete(DBHelper.TABLE, DBHelper.COL_ID + " = ?", new String[]{String.valueOf(id)});
                        refresh();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            return true;
        });
    }

    /** Перечитать записи из БД с учётом фильтра и сортировки, обновить статистику. */
    private void refresh() {
        buildWhere();
        Cursor cursor = db.query(DBHelper.TABLE, null, where, whereArgs, null, null, orderBy);
        adapter.changeCursor(cursor);
        updateStats();
        updateFilterBar();
    }

    // Сортировка
    private void setupSort() {
        Spinner spinner = findViewById(R.id.spinnerSort);
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, SORT_LABELS);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(sortAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                orderBy = SORT_ORDERS[position];
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    // Фильтр (выборка по интервалам)

    /** Собирает WHERE из заполненных полей фильтра. Пустые поля скипаются. */
    private void buildWhere() {
        List<String> conditions = new ArrayList<>();
        List<String> args = new ArrayList<>();

        if (durFrom != null) { conditions.add(DBHelper.COL_DURATION + " >= ?"); args.add(String.valueOf(durFrom)); }
        if (durTo != null)   { conditions.add(DBHelper.COL_DURATION + " <= ?"); args.add(String.valueOf(durTo)); }
        if (yearFrom != null) { conditions.add(DBHelper.COL_YEAR + " >= ?"); args.add(String.valueOf(yearFrom)); }
        if (yearTo != null)   { conditions.add(DBHelper.COL_YEAR + " <= ?"); args.add(String.valueOf(yearTo)); }
        if (!TextUtils.isEmpty(textQuery)) {
            conditions.add("(" + DBHelper.COL_ARTIST + " LIKE ? OR " + DBHelper.COL_TITLE + " LIKE ?)");
            args.add("%" + textQuery + "%");
            args.add("%" + textQuery + "%");
        }

        if (conditions.isEmpty()) {
            where = null;
            whereArgs = null;
        } else {
            where = TextUtils.join(" AND ", conditions);
            whereArgs = args.toArray(new String[0]);
        }
    }

    private boolean isFiltered() {
        return durFrom != null || durTo != null || yearFrom != null || yearTo != null
                || !TextUtils.isEmpty(textQuery);
    }

    private void clearFilter() {
        durFrom = durTo = yearFrom = yearTo = null;
        textQuery = null;
    }

    private void updateFilterBar() {
        if (!isFiltered()) {
            filterBar.setVisibility(View.GONE);
            return;
        }
        List<String> parts = new ArrayList<>();
        if (durFrom != null || durTo != null) {
            parts.add("длительность " + rangeText(
                    durFrom == null ? null : TimeUtils.format(durFrom),
                    durTo == null ? null : TimeUtils.format(durTo)));
        }
        if (yearFrom != null || yearTo != null) {
            parts.add("год " + rangeText(
                    yearFrom == null ? null : String.valueOf(yearFrom),
                    yearTo == null ? null : String.valueOf(yearTo)));
        }
        if (!TextUtils.isEmpty(textQuery)) {
            parts.add("«" + textQuery + "»");
        }
        tvFilterInfo.setText("Выборка: " + TextUtils.join(" · ", parts));
        filterBar.setVisibility(View.VISIBLE);
    }

    private String rangeText(String from, String to) {
        if (from != null && to != null) return from + "–" + to;
        if (from != null) return "от " + from;
        return "до " + to;
    }

    private void showFilterDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_filter, null);
        EditText etDurFrom = v.findViewById(R.id.etDurFrom);
        EditText etDurTo = v.findViewById(R.id.etDurTo);
        EditText etYearFrom = v.findViewById(R.id.etYearFrom);
        EditText etYearTo = v.findViewById(R.id.etYearTo);
        EditText etText = v.findViewById(R.id.etText);
        ((GlassLayout) v.findViewById(R.id.glassTitle)).setBlurTarget(v.findViewById(R.id.imgArt));
        roundTopCorners(v.findViewById(R.id.dialogHeader));

        // Подставляем текущие значения фильтра
        if (durFrom != null) etDurFrom.setText(TimeUtils.format(durFrom));
        if (durTo != null) etDurTo.setText(TimeUtils.format(durTo));
        if (yearFrom != null) etYearFrom.setText(String.valueOf(yearFrom));
        if (yearTo != null) etYearTo.setText(String.valueOf(yearTo));
        if (textQuery != null) etText.setText(textQuery);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setBackground(ContextCompat.getDrawable(this, R.drawable.dialog_bg))
                .setView(v)
                .setPositiveButton("Показать", null)
                .setNeutralButton("Сбросить", (d, w) -> {
                    clearFilter();
                    refresh();
                })
                .setNegativeButton("Отмена", null)
                .create();
        dialog.show();
        makeGlassWindow(dialog);

        // Свой обработчик, чтобы при ошибке окно НЕ закрывалось
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            Integer dFrom = readDuration(etDurFrom);
            Integer dTo = readDuration(etDurTo);
            Integer yFrom = readYear(etYearFrom);
            Integer yTo = readYear(etYearTo);
            if (dFrom != null && dFrom < 0 || dTo != null && dTo < 0
                    || yFrom != null && yFrom < 0 || yTo != null && yTo < 0) {
                return; // ошибка уже показана в поле
            }
            if (dFrom != null && dTo != null && dFrom > dTo) {
                etDurTo.setError("Верхняя граница меньше нижней");
                return;
            }
            if (yFrom != null && yTo != null && yFrom > yTo) {
                etYearTo.setError("Верхняя граница меньше нижней");
                return;
            }
            durFrom = dFrom;
            durTo = dTo;
            yearFrom = yFrom;
            yearTo = yTo;
            String q = etText.getText().toString().trim();
            textQuery = q.isEmpty() ? null : q;

            refresh();
            dialog.dismiss();
        });
    }

    /** null — поле пустое; -1 — ошибка */
    private Integer readDuration(EditText et) {
        String s = et.getText().toString().trim();
        if (s.isEmpty()) return null;
        int sec = TimeUtils.parse(s);
        if (sec < 0) {
            et.setError("Формат: 3:45 или просто минуты");
            return -1;
        }
        return sec;
    }

    private Integer readYear(EditText et) {
        String s = et.getText().toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            et.setError("Введите год числом");
            return -1;
        }
    }

    // Добавление
    private void showAddDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add, null);
        EditText etArtist = v.findViewById(R.id.etArtist);
        EditText etTitle = v.findViewById(R.id.etTitle);
        EditText etYear = v.findViewById(R.id.etYear);
        EditText etDuration = v.findViewById(R.id.etDuration);
        ((GlassLayout) v.findViewById(R.id.glassTitle)).setBlurTarget(v.findViewById(R.id.imgArt));
        roundTopCorners(v.findViewById(R.id.dialogHeader));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setBackground(ContextCompat.getDrawable(this, R.drawable.dialog_bg))
                .setView(v)
                .setPositiveButton("Добавить", null)
                .setNegativeButton("Отмена", null)
                .create();
        dialog.show();
        makeGlassWindow(dialog);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            String artist = etArtist.getText().toString().trim();
            String title = etTitle.getText().toString().trim();
            String yearText = etYear.getText().toString().trim();
            int duration = TimeUtils.parse(etDuration.getText().toString());
            int maxYear = Calendar.getInstance().get(Calendar.YEAR) + 1;

            boolean ok = true;
            if (artist.isEmpty()) { etArtist.setError("Укажите исполнителя"); ok = false; }
            if (title.isEmpty()) { etTitle.setError("Укажите название"); ok = false; }

            int year = -1;
            try {
                year = Integer.parseInt(yearText);
            } catch (NumberFormatException ignored) { }
            if (year < 1900 || year > maxYear) { etYear.setError("Год от 1900 до " + maxYear); ok = false; }

            if (duration <= 0) { etDuration.setError("Формат: 3:45"); ok = false; }
            if (!ok) return;

            ContentValues cv = new ContentValues();
            cv.put(DBHelper.COL_ARTIST, artist);
            cv.put(DBHelper.COL_TITLE, title);
            cv.put(DBHelper.COL_YEAR, year);
            cv.put(DBHelper.COL_DURATION, duration);
            db.insert(DBHelper.TABLE, null, cv);

            refresh();
            dialog.dismiss();
            Toast.makeText(this, "Трек «" + title + "» добавлен", Toast.LENGTH_SHORT).show();
        });
    }

    // Статистика
    /** Количество, суммарная и максимальная длительность — по текущей выборке */
    private void updateStats() {
        String sql = "SELECT COUNT(*), IFNULL(SUM(" + DBHelper.COL_DURATION + "), 0) FROM " + DBHelper.TABLE
                + (where != null ? " WHERE " + where : "");
        int count = 0, total = 0;
        try (Cursor c = db.rawQuery(sql, whereArgs)) {
            if (c.moveToFirst()) {
                count = c.getInt(0);
                total = c.getInt(1);
            }
        }

        String longest = "—";
        try (Cursor c = db.query(DBHelper.TABLE,
                new String[]{DBHelper.COL_TITLE, DBHelper.COL_DURATION},
                where, whereArgs, null, null, DBHelper.COL_DURATION + " DESC", "1")) {
            if (c.moveToFirst()) {
                longest = c.getString(0) + " (" + TimeUtils.format(c.getInt(1)) + ")";
            }
        }

        tvCount.setText("◆  Треков: " + count);
        tvTotal.setText("◆  Общая длительность: " + TimeUtils.format(total));
        tvLongest.setText("◆  Самый длинный: " + longest);
    }

    /** Скругляет верхние углы шапки диалога под форму окна. */
    private void roundTopCorners(View header) {
        final float r = 28 * getResources().getDisplayMetrics().density;
        header.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // прямоугольник выше на радиус вниз — скруглены только верхние углы
                outline.setRoundRect(0, 0, view.getWidth(), (int) (view.getHeight() + r), r);
            }
        });
        header.setClipToOutline(true);
    }

    /**
     * Стеклянное окно диалога
     */
    private void makeGlassWindow(AlertDialog dialog) {
        Window w = dialog.getWindow();
        if (w == null) return;
        w.setDimAmount(0.15f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.setBlurBehindRadius(40);
            w.setAttributes(lp);
        }
    }

    @Override
    protected void onDestroy() {
        adapter.changeCursor(null);
        dbHelper.close();
        super.onDestroy();
    }
}
