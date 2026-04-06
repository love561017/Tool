package utils;
/**
 * <table>
 * <tr>
 * <th>版本</th>
 * <th>日期</th>
 * <th>詳細說明</th>
 * <th>modifier</th>
 * </tr>
 * <tr>
 * <td>1.0</td>
 * <td>2026年1月29日</td>
 * <td>新建檔案</td>
 * <td>Jason</td>
 * </tr>
 * </table>
 * @author Jason
 *
 * 類別說明 :
 *
 *
 * 版權所有 Copyright 2008 © 中菲電腦股份有限公司 本網站內容享有著作權，禁止侵害，違者必究。 <br>
 * (C) Copyright Dimerco Data System Corporation Inc., Ltd. 2009 All Rights
 */
//import java.io.File;
//import java.io.FileOutputStream;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//import org.apache.commons.lang.StringUtils;
//import org.apache.poi.ss.usermodel.Cell;
//import org.apache.poi.ss.usermodel.Row;
//import org.apache.poi.ss.usermodel.Sheet;
//import org.apache.poi.xssf.streaming.SXSSFWorkbook;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

public class AmExcelUtils {
//	private static final Logger logger = LoggerFactory.getLogger(AmExcelUtils.class);
//
//	public static void exportToExcel(AmExcelHelper helper, String sheetName, List<String[]> dataList) throws Exception {
//		if (null == helper.getFile()) {
//			throw new Exception("No export Excel file name set");
//		}
//		String safeSheetName = sheetName.replaceAll("[\\\\/?*\\[\\]]", "_");
//		if (helper.getSheetNameMap().containsKey(sheetName)) {
//			safeSheetName = helper.getSheetNameMap().get(sheetName);
//		}
//		else {
//			if (safeSheetName.length() > 31) {
//				safeSheetName = safeSheetName.substring(0, 31);
//			}
//			safeSheetName = helper.putSheetNameMap(sheetName, safeSheetName);
//		}
//		Sheet sheet = helper.getWorkbook().getSheet(safeSheetName);
//		if (null == sheet) {
//			sheet = helper.getWorkbook().createSheet(safeSheetName);
//		}
//		int rowIndex = sheet.getPhysicalNumberOfRows();
//		for (String[] data : dataList) {
//			Row dataRow = sheet.createRow(rowIndex);
//			rowIndex++;
//			int cellIndex = 0;
//			for (String val : data) {
//				Cell cell = dataRow.createCell(cellIndex++);
//				if (val.length() > 32767) {
//					val = StringUtils.left(val, 32767);
//					logger.error("檔案名稱={}、頁籤={}、第{}列、第{}欄，欄位長度超過32767", new Object[] { helper.getFile().getName(), safeSheetName, rowIndex, cellIndex });
//				}
//				cell.setCellValue(val);
//			}
//		}
//	}
//
//	public static class AmExcelHelper implements AutoCloseable {
//		private SXSSFWorkbook workbook;
//		private File file;
//		private Map<String, String> sheetNameMap;
//		private Map<String, Integer> safeSheetNameMap;
//
//		public AmExcelHelper() {
//			workbook = new SXSSFWorkbook(0);
//		}
//
//		public void setFileName(String fileName) {
//			File dir = new File(DataHelper.LOCAL_PATH + Constants.SEPARATOR + "excelTmpFile");
//			if (!dir.exists()) {
//				dir.mkdirs();
//			}
//			file = new File(dir, fileName);
//		}
//
//		@Override
//		public void close() throws Exception {
//			try {
//				write();
//			}
//			finally {
//				if (workbook != null) {
//					workbook.close();
//				}
//			}
//		}
//
//		public String putSheetNameMap(String sheetName, String safeSheetName) {
//			int index = 0;
//			String finalSheetName = safeSheetName;
//			if (safeSheetNameMap.containsKey(safeSheetName)) {
//				index = safeSheetNameMap.get(safeSheetName) + 1;
//				finalSheetName = StringUtils.left(safeSheetName, 28) + "_" + index;
//			}
//			safeSheetNameMap.put(safeSheetName, index);
//			getSheetNameMap().put(sheetName, finalSheetName);
//			return finalSheetName;
//		}
//
//		private void write() throws Exception {
//			if (null == file) {
//				throw new Exception("No export Excel file name set");
//			}
//			try (FileOutputStream fos = new FileOutputStream(file)) {
//				workbook.write(fos);
//			}
//		}
//
//		public SXSSFWorkbook getWorkbook() {
//			return workbook;
//		}
//
//		public void setWorkbook(SXSSFWorkbook workbook) {
//			this.workbook = workbook;
//		}
//
//		public File getFile() {
//			return file;
//		}
//
//		public void setFile(File file) {
//			this.file = file;
//		}
//
//		public Map<String, String> getSheetNameMap() {
//			if (null == sheetNameMap) {
//				sheetNameMap = new HashMap<>();
//				safeSheetNameMap = new HashMap<>();
//			}
//			return sheetNameMap;
//		}
//
//		public void setSheetNameMap(Map<String, String> sheetNameMap) {
//			this.sheetNameMap = sheetNameMap;
//		}
//
//	}
}
