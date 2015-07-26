package com.kivatek.mpjackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.msgpack.jackson.dataformat.msgpack.MessagePackFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.TreeMap;

public class Application {
    public static void main(String[] args) throws Exception {
        new Application().process(args);
    }

    static final SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    public void process(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream("sample.xlsx");
            Workbook wb = WorkbookFactory.create(inputStream);

            // シートを取得
            Sheet sheet = wb.getSheet("Sheet1");
            if (sheet == null) {
                System.out.println("Sheet not found.");
                return;
            }
            // 項目名、型を取得
            Row keyRow = sheet.getRow(0);
            Row typeRow = sheet.getRow(1);
            short numberOfCells = keyRow.getLastCellNum();
            Map<Integer, KeyType> fieldMap = new TreeMap<>();
            for (int i = 1; i < numberOfCells; i++) {
                try {
                    Cell keyCell = keyRow.getCell(i);
                    Cell typeCell = typeRow.getCell(i);
                    if (isBlankCell(keyCell) == false && isBlankCell(typeCell) == false) {
                        KeyType kt = new KeyType();
                        kt.fieldName = keyCell.getStringCellValue();
                        kt.fieldType = typeCell.getStringCellValue();
                        fieldMap.put(i, kt);
                    }
                } catch (NullPointerException e) {
                }
            }

            Team team = new Team();
            for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                numberOfCells = row.getLastCellNum();
                Cell cell = row.getCell(0);
                if (cell != null && (isBlankCell(cell) || cell.getStringCellValue().startsWith("#"))) {
                    continue;
                }

                Person person = new Person();
                for (int colIndex = 1; colIndex < numberOfCells; colIndex++) {
                    cell = row.getCell(colIndex);
                    if (cell == null || isBlankCell(cell)) {
                        continue;
                    }
                    if (fieldMap.containsKey(colIndex)) {
                        String content = getContentString(cell);
                        String fieldName = fieldMap.get(colIndex).fieldName;
                        setValueToField(person, fieldName, content);
//                        System.out.println(fieldMap.get(colIndex).fieldName + ":" + fieldMap.get(colIndex).fieldType + ":" + content);
                    }
                }
                team.memberData.add(objectMapper.writeValueAsBytes(person));
            }
            // teamをいったんbyte配列に変換
            // この配列をファイルへ書き出すことでマスターデータファイルとして使用する
            byte[] teamBinary = objectMapper.writeValueAsBytes(team);
            {
                StringBuilder sb = new StringBuilder();
                for (byte b : teamBinary) {
                    int value = b & 0xff;
                    sb.append(String.format("0x%2s", Integer.toHexString(value)).replace(' ', '0')).append(",");
                }
                System.out.println(sb.toString());
            }

            // byte配列からTeamのインスタンスを復元
            Team decodedTeam = objectMapper.readValue(teamBinary, Team.class);
            for (byte[] array : decodedTeam.memberData) {
                Person decodedPerson = objectMapper.readValue(array, Person.class);
                System.out.println(decodedPerson.id + ":" + decodedPerson.firstName + ":" + decodedPerson.familyName + ":" + decodedPerson.age);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (InvalidFormatException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    public boolean isBlankCell(Cell cell) {
        switch (cell.getCellType()) {
            case Cell.CELL_TYPE_BLANK:
                return true;
            case Cell.CELL_TYPE_STRING:
                return StringUtils.isBlank(cell.getStringCellValue());
        }
        return false;
    }

    /**
     * Cellの内容を文字列として取得。数値もいったん文字列で取得する。
     * @param cell
     * @return
     */
    private String getContentString(Cell cell) {
        // poiでは内容がないcellの情報は本当にないものとして扱われる。
        // ところが長さ0の文字列など見かけの情報がなくてもcellの情報が存在することはあり別途チェックすることになる。
        if (isBlankCell(cell) == false) {
            switch (cell.getCellType()) {
                case Cell.CELL_TYPE_FORMULA:
                    // 書式が設定されている場合はCELL_TYPE_NUMERIC、CELL_TYPE_STRING、CELL_TYPE_BOOLEANの違いが分からない
                    // そのため強制的に値の取得を繰り返す
                    try {
                        return cell.getStringCellValue().trim();
                    } catch (Exception e) {
                    }
                    try {
                        return String.valueOf(Double.valueOf(cell.getNumericCellValue()));
                    } catch (Exception e) {
                    }
                    return String.valueOf(cell.getBooleanCellValue());
                case Cell.CELL_TYPE_NUMERIC:
                    return String.valueOf(Double.valueOf(cell.getNumericCellValue()));
                case Cell.CELL_TYPE_STRING:
                    return cell.getStringCellValue().trim();
                case Cell.CELL_TYPE_BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
            }
        }
        return "";
    }

    /**
     * 指定された名前のフィールドを返す。
     * @param obj
     * @param fieldName
     * @return
     */
    private Field getField(Object obj, String fieldName) {
        try {
            String className = obj.getClass().getName();
            Class<?> clazz = Class.forName(className);
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 指定した名前のフィールドへ値を設定する
     * @param obj
     * @param fieldName
     * @param content
     */
    private void setValueToField(Object obj, String fieldName, String content) {
        Field field = getField(obj, fieldName);
        if (field == null) {
            return;
        }
        Type type = field.getGenericType();
        try {
            switch (type.toString().replace("class ", "")) {
                case "java.lang.String":
                    field.set(obj, content);
                    break;
                case "java.util.Date":
                    field.set(obj, inputDateFormat.parse(content));
                    break;
                case "int":
                    content = (content.length() == 0) ? "0" : content;
                    field.setInt(obj, (int)Double.parseDouble(content));
                    break;
                case "long":
                    content = (content.length() == 0) ? "0" : content;
                    field.setLong(obj, (long)Double.parseDouble(content));
                    break;
                case "float":
                    content = (content.length() == 0) ? "0.0f" : content;
                    field.setFloat(obj, Float.parseFloat(content));
                    break;
                case "double":
                    content = (content.length() == 0) ? "0.0" : content;
                    field.setDouble(obj, Double.parseDouble(content));
                    break;
                case "boolean":
                    boolean b = false;
                    if (content.length() > 0) {
                        content = content.trim().toLowerCase();
                        // 文字列が「0」「０」「false」でなければ true とみなす。
                        b = (content.equals("false") == false && content.equals("0") == false && content.equals("０") == false);
                    }
                    field.setBoolean(obj, b);
                    break;
            }
        } catch (IllegalArgumentException | IllegalAccessException | ParseException e) {
            e.printStackTrace();
        }
    }
}
