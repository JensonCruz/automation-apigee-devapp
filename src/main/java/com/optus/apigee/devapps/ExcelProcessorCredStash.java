package com.optus.apigee.devapps;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelProcessorCredStash {
	public static void main(String[] args) throws IOException {
		// Load properties from application.properties file
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Properties properties = new Properties();
		try (InputStream resourceStream = loader.getResourceAsStream("application.properties")) {
			properties.load(resourceStream);
		} catch (IOException e) {
			e.printStackTrace();
		}

		String excelFilePath;
		String folderBasePath;

//		if (args.length < 2) {
//			System.out
//					.println("Please provide the 'excel.file.path' and 'folder.base.path' as command-line arguments.");
//			return;
//		}

		if (args.length >= 2) {
			excelFilePath = args[0];
			folderBasePath = args[1];
			System.out.println("Cmd line of excel path: " + excelFilePath);
			System.out.println("Cmd line of folderBasePath: " + folderBasePath);
		} else {

			excelFilePath = properties.getProperty("credstash.file.path");
			folderBasePath = properties.getProperty("folder.base.path");
		}

		// Read Excel file

		FileInputStream inputStream = null;
		Workbook workbook = null;
		try {
			inputStream = new FileInputStream(excelFilePath);
			workbook = new XSSFWorkbook(inputStream);

			// Rest of your code here

			Sheet sheet = workbook.getSheetAt(0);

			// Get column indexes based on headers
			int credStashColumnIndex = getColumnIndex(sheet, "CredStash");
			int passwordColumnIndex = getColumnIndex(sheet, "tst5");

			// Move to the next row (skip the column header row)
			Iterator<Row> rowIterator = sheet.iterator();
			rowIterator.next(); // Move to the next row (skip the column header row)

			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
			    Cell credStashCell = row.getCell(credStashColumnIndex);
			    if (credStashCell == null || credStashCell.getCellType() == CellType.BLANK) {
			        System.out.println("Cred is null");
			        continue;
			    }
			    String credStash = credStashCell.getStringCellValue();
			    System.out.println("Cred value:"+credStash);

			    Cell passwordCell = row.getCell(passwordColumnIndex);
			    if (passwordCell == null || passwordCell.getCellType() == CellType.BLANK) {
			        System.out.println("Password is null");
			        continue;
			    }
				String passwordExcel = passwordCell.getStringCellValue();

				// Update folder and file based on the username
				updateDevAppConfFile(getUserName(credStash), getPassword(credStash),passwordExcel, folderBasePath);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (workbook != null) {
				workbook.close();
			}
			if (inputStream != null) {
				inputStream.close();
			}
		}
	}

	private static String getUserName(String credStash) {

		System.out.println(credStash);
		// Find the start index of the desired substring
		int startIndex = credStash.indexOf("gcp.apigeex-");

		// Check if the substring "gcp.apigeex-" is found
		if (startIndex == -1) {
			System.out.println("Substring 'gcp.apigeex-' not found.");
			return null; // Return null or any other value that suits your requirements
		}

		// Find the end index of the desired substring
		int endIndex = credStash.indexOf("-dapp-api-config", startIndex);

		// Check if the substring "-dapp-api-config" is found after "gcp.apigeex-"
		if (endIndex == -1) {
			System.out.println("Substring '-dapp-api-config' not found after 'gcp.apigeex-'.");
			return null; // Return null or any other value that suits your requirements
		}

		// Extract the desired substring
		String extractedUsername = credStash.substring(startIndex + "gcp.apigeex-".length(), endIndex);

		System.out.println("Extracted username: " + extractedUsername);
		return extractedUsername;
	}

	private static String getPassword(String credStash) {

		return "$(credstash get " + credStash.trim() + ")";
	}

	private static void updateDevAppConfFile(String username, String password, String passwordExcel, String folderBasePath)
			throws IOException {

		String basePath = folderBasePath;
		String folderPath = basePath + "/" + username;
		File folder = new File(folderPath);

		String filePath = folder.getAbsolutePath() + "/dev-app.conf";
		System.out.println("File path:" + filePath);
		File configFile = new File(filePath);
		if (configFile.exists()) {
			List<String> lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);

			boolean foundDappApiSecret = false;
			boolean foundDappApproval = false;

			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);

				if (line.startsWith("DAPP_APPROVAL_TYPE=")) {
					lines.remove(i); // Remove the line containing "DAPP_APPROVAL_TYPE="
					foundDappApproval = true;
					i--; // Decrement the index to correctly process the next line
				} else if (line.startsWith("DAPP_API_SECRET=")) {
//					lines.add("#DAPP_API_SECRET=\"" + passwordExcel + "\"\n");
					lines.set(i,"#DAPP_API_SECRET=\"" + passwordExcel + "\"\n"+ "DAPP_API_SECRET= " + password); // Replace the line containing "DAPP_API_SECRET=" with
																	// the provided password
					foundDappApiSecret = true;
				}
			}

			// Add missing lines if not found
			if (!foundDappApproval) {
				lines.add("");
			}

			if (!foundDappApiSecret) {
				lines.add("DAPP_API_SECRET= " + password);
			}

			// Remove trailing white spaces at the end of the file
			while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) {
				lines.remove(lines.size() - 1);
			}
			// Write the updated lines back to the file
			Files.write(Paths.get(filePath), lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
			System.out.println("dev-app.conf file updated: " + filePath);
		} else {
			System.out.println("dev-app.conf file does not exist: " + filePath);
		}
	}

	private static int getColumnIndex(Sheet sheet, String header) {
		Row headerRow = sheet.getRow(0);
		Iterator<Cell> cellIterator = headerRow.cellIterator();
		while (cellIterator.hasNext()) {
			Cell cell = cellIterator.next();
			if (cell.getStringCellValue().equalsIgnoreCase(header)) {
				return cell.getColumnIndex();
			}
		}
		return -1;
	}
}