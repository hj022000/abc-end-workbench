package com.abc.datamining.common.document;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.abc.datamining.adaboost.utils.IdentityUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;
import com.abc.datamining.utils.FileUtils;
//import org.project.utils.IdentityUtils;
import com.abc.datamining.utils.RandomUtils;
import com.abc.datamining.utils.SegUtils;
import com.abc.datamining.utils.WordUtils;
import com.abc.datamining.utils.http.HttpClientUtils;
import com.abc.datamining.utils.http.HttpUtils;
import com.abc.datamining.utils.jdbc.JDBCUtils;

import com.chenlb.mmseg4j.Seg;

public class DocumentLoader {
	
	protected static Logger logger = LoggerFactory.getLogger(DocumentLoader.class);
	
	private static ExecutorService executorService = 
			Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	
	public static void main(String[] args) throws Exception {
		long start = System.currentTimeMillis();
		String path = "D:\\resources\\data\\res2\\";
		DocumentSet documentSet = DocumentLoader.loadDocumentSetByThread(path);
		List<Document> documents = documentSet.getDocuments();
		DocumentUtils.calculateTFIDF_0(documents);
		long end = System.currentTimeMillis();
		System.out.println("spend time: " + (end - start) / 1000);
		System.exit(0);
	}
	
	public static DocumentSet loadDocumentSet(String path) {
		DocumentSet documentSet = new DocumentSet();
		documentSet.setDocuments(loadDocumentList(path));
		return documentSet;
	}
	
	public static DocumentSet loadDocumentSetByThread(String path) {
		DocumentSet documentSet = new DocumentSet();
		documentSet.setDocuments(loadDocumentListByThread(path));
		return documentSet;
	}
	
	public static List<Document> loadDocumentList(String path) {
		List<Document> docs = new ArrayList<Document>();
		Seg seg = SegUtils.getComplexSeg();
		File[] files = FileUtils.obtainFiles(path);
		for (File file : files) {
			Document document = new Document();
			document.setCategory(file.getParentFile().getName());
			document.setName(file.getName());
			document.setWords(WordUtils.splitFile(file, seg, 2));
			docs.add(document);
		}
		return docs;
	}
	
	public static List<Document> loadDocumentListByThread(String path) {
		List<Future<Document>> futures = new ArrayList<Future<Document>>();
		String[] filePaths = FileUtils.obtainFilePaths(path);
		for (String filePath : filePaths) {
			futures.add(executorService.submit(new FileToDocumentThread(filePath)));
		}
		List<Document> documents = new ArrayList<Document>();
		for (Future<Document> future : futures) {
			try {
				Document document = future.get(30, TimeUnit.SECONDS);
				System.out.println("document: " + document.getName() + " finish!");
				documents.add(document);
			} catch (Exception e) {
				e.printStackTrace();
			} 
		}
		return documents;
	}
	
	public static DocumentSet loadCrossDocumentList(String path) {
		DocumentSet documentSet = new DocumentSet();
		List<Document> documents = loadDocumentList(path);
		Map<String, List<Document>> cateToDocs = new HashMap<String, List<Document>>();
		for (Document document : documents) {
			String category = document.getCategory();
			List<Document> docs = cateToDocs.get(category);
			if (null == docs) {
				docs = new ArrayList<Document>();
				cateToDocs.put(category, docs);
			}
			docs.add(document);
		}
		for (Map.Entry<String, List<Document>> entry : cateToDocs.entrySet()) {
			List<Document> docs = entry.getValue();
			for (int i = 0, len = docs.size(), limit = len / 5; i < limit; i++, len--) {
				documentSet.getTestDocuments().add(docs.remove(RandomUtils.nextInt(len)));
			}
			documentSet.getTrainDocuments().addAll(docs);
		}
		return documentSet;
	}
	
	
	public static void loadURLToFile() {
		String sql = "select url from doc where channel = 'tech' and source = '163' limit 0,1";
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet result = null;
		try{
			conn = JDBCUtils.obtainConnection();
			pstmt = conn.prepareStatement(sql);
			result = pstmt.executeQuery();
			while(result.next()) {
//				String url = result.getString(1);
				String url = "http://ent.163.com/14/0819/10/A40NM42F000300B1.html";
				String content = HttpClientUtils.get(url, HttpUtils.ENCODE_GB2312);
				org.jsoup.nodes.Document document = Jsoup.parse(content);
				Elements divs = document.select("div");
				StringBuilder sb = new StringBuilder();
				for (Element div : divs) {
					if(!div.hasAttr("id")) {
						continue;
					}
					String divId = div.attr("id");
					if ("endText".equals(divId)) {
						sb.append(cleanText(div.text())).append("\n");
					} else if ("epContentLeft".equals(divId)) {
						Elements h1s = div.select("h1");
						for (Element h1 : h1s) {
							if(!h1.hasAttr("id")) {
								continue;
							}
							String h1Id = h1.attr("id");
							if ("h1title".equals(h1Id)) {
								sb.append(cleanText(h1.text())).append("\n");
							} 
						}
					}
				}
				System.out.println(sb.toString());
				writeFile("教育", sb.toString());
			}
		} catch (Exception e) {
			logger.info(e.getMessage(), e);
		} finally{
			JDBCUtils.returnConnection(conn);
			JDBCUtils.release(null, pstmt, result);
		}
	}
	
	public static String cleanText(String text) {
		return Jsoup.clean(text, Whitelist.none()).replace("&nbsp;"," ").replace("&middot;", ".");
	}
	
	public static void writeFile(String category, String context) {
		OutputStream out = null;
		BufferedWriter bw = null;
		try {
			String path = DocumentLoader.class.getClassLoader().getResource("新闻").toURI().getPath();
			path += File.separator + category + File.separator;
			File parent = new File(path);
			if (!parent.exists()) parent.mkdirs();
			out = new FileOutputStream(new File(path + IdentityUtils.generateUUID()));
			bw = new BufferedWriter(new OutputStreamWriter(out));
			bw.write(context);
			bw.flush();
		} catch (Exception e) {
			logger.info(e.getMessage(), e);
		} finally{
			IOUtils.closeQuietly(out);
			IOUtils.closeQuietly(bw);
		}
	}
	
}

class FileToDocumentThread implements Callable<Document> {

	private String filePath = null;
	
	public FileToDocumentThread(String filePath) {
		this.filePath = filePath;
	}
	
	@Override
	public Document call() throws Exception {
		File file = new File(filePath);
		Document document = new Document();
		document.setCategory(file.getParentFile().getName());
		document.setName(file.getName());
		document.setWords(WordUtils.splitFile(file, SegUtils.getComplexSeg(), 2));
		return document;
	}
	
}
