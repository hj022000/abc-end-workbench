package com.abc.datamining.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import org.ansj.domain.Term;
//import org.ansj.splitWord.analysis.NlpAnalysis;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
//import org.tartarus.snowball.SnowballProgram;

import com.chenlb.mmseg4j.MMSeg;
import com.chenlb.mmseg4j.Seg;
import com.chenlb.mmseg4j.Word;

public class WordUtils {
	
	public static Logger logger = Logger.getLogger(WordUtils.class);
	
	/* 分隔符的集合 */
	public static final String DELIMITERS = "\t\n\r\f~!@#$%^&*()_+|`-=\\{}[]:\";'<>?,./'";
	
	public static String[] splitByNlp(String input) {
		return splitByNlp(input, 1); 
	}
	
	public static String[] splitByNlp(String input, int minLength) {
		List<String> words = new ArrayList<String>();
//		List<Term> terms = NlpAnalysis.parse(input);
//		for (Term term : terms) {
//			String word = term.getName();
//			if (word.length() >= minLength) words.add(word);
//		}
		return removeWords(words.toArray(new String[0])); 
	}
	
	public static String[] splitBySeg(String input) {
		return split(new StringReader(input), SegUtils.getComplexSeg());
	}
	
	/**
	 * 分词
	 * @param input 输入文本
	 * @param seg 分词器
	 * @return
	 */
	public static String[] split(String input, Seg seg) {
		return split(new StringReader(input), seg);
	}
	
	public static String[] split(Reader reader, Seg seg) {
		return split(reader, seg, 1);
	}
	
	public static String[] split(Reader reader, Seg seg, int minLength) {
		List<String> words = new ArrayList<String>();
		MMSeg mmSeg = new MMSeg(reader, seg);
		Word word = null;
		try {
			while ((word = mmSeg.next()) != null) {
				String w = word.getString();
				if (w.length() >= minLength) words.add(w);
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
		return removeWords(words.toArray(new String[0]));
	}
	
	/**
	 * 分词
	 * @param input 输入文本
	 * @param language 语言
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static String[] split(String input, String language) {
		/* 根据分隔符分词 */
		StringTokenizer stringTokenizer = new StringTokenizer(input, DELIMITERS);
		/* 所有的词 */
		Vector vector = new Vector();
		/* 全大写的词 -- 不用提词干所以单独处理 */
		Vector vectorForAllUpperCase = new Vector();
		/* 根据大写字母分词 */
		flag0: while (stringTokenizer.hasMoreTokens()) {
			String token = stringTokenizer.nextToken();
			/* 全大写的词单独处理 */
			boolean allUpperCase = true;
			for (int i = 0; i < token.length(); i++) {
				if (!Character.isUpperCase(token.charAt(i))) {
					allUpperCase = false;
				}
			}
			if (allUpperCase) {
				vectorForAllUpperCase.addElement(token);
				continue flag0;
			}
			/* 非全大写的词 */
			int index = 0;
			flag1: while (index < token.length()) {
				flag2: while (true) {
					index++;
					if ((index == token.length())
							|| !Character.isLowerCase(token.charAt(index))) {
						break flag2;
					}
				}
				vector.addElement(token.substring(0, index).toLowerCase());
				token = token.substring(index);
				index = 0;
				continue flag1;
			}
		}
		/* 提词干 */
//		try {
//			Class stemClass = Class.forName("org.tartarus.snowball.ext."
//					+ language + "Stemmer");
//			SnowballProgram stemmer = (SnowballProgram) stemClass.newInstance();
//			Method stemMethod = stemClass.getMethod("stem", new Class[0]);
//			Object[] emptyArgs = new Object[0];
//			for (int i = 0; i < vector.size(); i++) {
//				stemmer.setCurrent((String) vector.elementAt(i));
//				stemMethod.invoke(stemmer, emptyArgs);
//				vector.setElementAt(stemmer.getCurrent(), i);
//			}
//		} catch (Exception e) {
//			logger.error(e.getMessage());
//		}
		/* 合并全大写的词 */
		for (int i = 0; i < vectorForAllUpperCase.size(); i++) {
			vector.addElement(vectorForAllUpperCase.elementAt(i));
		}
		/* 转为数组形式 */
		String[] array = new String[vector.size()];
		Enumeration enumeration = vector.elements();
		int index = 0;
		while (enumeration.hasMoreElements()) {
			array[index] = (String) enumeration.nextElement();
			index++;
		}
		/* 打印显示 */
		for (int i = 0; i < array.length; i++) {
			System.out.print(array[i] + " ");
		}
		return removeWords(array);
	}
	
	/**
	 * 英文分词
	 * @param input 输入文本
	 * @return
	 */
	public static String[] splitEN(String input) {
		return split(input, "english");
	}
	
	public static String[] splitFile(String path) {
		File file = new File(path);
		if (!file.exists()) {
			logger.error("file not exists");
		}
		return splitFile(file);
	}
	
	public static String[] splitFile(File file) {
		String[] words = null;
		InputStream in = null;
		BufferedReader reader = null;
		try {
			in = new FileInputStream(file);
			reader = new BufferedReader(new InputStreamReader(in));
			StringBuilder sb = new StringBuilder();
			String line = reader.readLine();
			while (null != line) {
				sb.append(line);
				line = reader.readLine();
			}
			words = splitByNlp(sb.toString());
		} catch (Exception e) {
			logger.error(e.getMessage());
		}  finally {
			IOUtils.closeQuietly(in);
			IOUtils.closeQuietly(reader);
		}
		return removeWords(words);
	}
	
	/**
	 * 文件分词
	 * @param path
	 * @param seg
	 * @param minLength
	 * @return
	 */
	public static String[] splitFile(String path, Seg seg, int minLength) {
		File file = new File(path);
		if (!file.exists()) {
			logger.error("file not exists");
		}
		return splitFile(file, seg, minLength);
	}
	
	/**
	 * 文件分词
	 * @param path
	 * @param seg
	 * @return
	 */
	public static String[] splitFile(File file, Seg seg, int minLength) {
		String[] words = null;
		InputStream in = null;
		BufferedReader reader = null;
		try {
			in = new FileInputStream(file);
			reader = new BufferedReader(new InputStreamReader(in));
			words = split(reader, seg, minLength);
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage());
		} finally {
			IOUtils.closeQuietly(in);
			IOUtils.closeQuietly(reader);
		}
		return removeWords(words);
	}
	
	/**
	 * 目录分词
	 * @param path
	 * @param seg
	 * @return
	 */
	public static String[] splitDirectory(String path, Seg seg, int minLength) {
		File directory = new File(path);
		if (!directory.exists()) {
			logger.error("directory not exists");
		}
		String[] all_words = null;
		for (File file : directory.listFiles()) {
			String[] words = splitFile(file, seg, minLength);
			if (null == all_words) {
				all_words = words;
				continue;
			}
			String[] new_words = new String[all_words.length + words.length];
			System.arraycopy(all_words, 0, new_words, 0, all_words.length);
			System.arraycopy(words, 0, new_words, all_words.length, words.length);
			all_words = new_words;
		}
		return all_words;
	}
	
	public static String[] removeWords(String[] words) {
		return removeStopWords(words);
	}
	
	/**
	 * 移除停用词
	 * @param words
	 * @return
	 */
	public static String[] removeStopWords(String[] words) {
		List<String> filterWords = new ArrayList<String>();
		String regEx = "[0-9]";
		Pattern pattern = Pattern.compile(regEx);
		for (String word : words) {
			if (isStopWord(word)) continue;
			Matcher matcher = pattern.matcher(word);
			if (matcher.find()) continue; 
			filterWords.add(word);
		}
		return filterWords.toArray(new String[0]);
	}
	
	/**
	 * 移除数字
	 * @param words
	 * @return
	 */
	public static String[] removeNumericalWords(String[] words) {
		List<String> filterWords = new ArrayList<String>();
		String regEx = "[0-9]";
		Pattern pattern = Pattern.compile(regEx);
		for (String word : words) {
			Matcher matcher = pattern.matcher(word);
			if (!matcher.find()) {
				filterWords.add(word);
			}
		}
		return filterWords.toArray(new String[0]);
	}
	
	/**
	 * 合并并且去重
	 * @param w1
	 * @param w2
	 * @return
	 */
	public static String[] mergeAndRemoveRepeat(String[] w1, String[] w2) {
		Set<String> words = new HashSet<String>();
		for (String word : w1) {
			words.add(word);
		}
		for (String word : w2) {
			words.add(word);
		}
		return words.toArray(new String[0]);
	}
	
	/**
	 * 是否是停用词
	 * @param word
	 * @return
	 */
	public static boolean isStopWord(String word) {
		return DictionaryUtils.getStopWords().contains(word);
	}
	
	/**
	 * 是否是情感词
	 * @param word
	 * @return
	 */
	public static boolean isEmotionWord(String word) {
		Boolean isEmotion = DictionaryUtils.getCommendatoryWords().containsKey(word);
		if (null == isEmotion || !isEmotion) {
			isEmotion = DictionaryUtils.getDerogratoryWords().containsKey(word);
		}
		return null == isEmotion ? false : isEmotion;
	}
	
	/**
	 * 是否是副词
	 * @param word
	 * @return
	 */
	public static boolean isAdverbsWord(String word) {
		return DictionaryUtils.getAdverbsWords().containsKey(word);
	}
	
	/**
	 * 是否是否定词
	 * @param word
	 * @return
	 */
	public static boolean isNegativeWord(String word) {
		return DictionaryUtils.getNegativeWords().containsKey(word);
	}
	
	/**
	 * 是否是感叹句
	 * @param sentence
	 * @return
	 */
	public static boolean isExclamatorySentence(String sentence) {
		int index = sentence.indexOf("!");
		if (index == -1) {
			index = sentence.indexOf("！");
		}
		return index == -1 ? false : true;
	}
	
	/**
	 * 是否是反问句
	 * @param sentence
	 * @return
	 */
	public static boolean isRhetoricalSentence(String sentence) {
		int index = sentence.indexOf("?");
		if (index == -1) {
			index = sentence.indexOf("？");
		}
		if (index == -1) return false;
		String[] words = splitByNlp(sentence);
		for (String word : words) {
			if (null != DictionaryUtils.getRhetoricalWords().get(word)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * 获取词语权重
	 * @param word
	 * @return
	 */
	public static double getWordWeight(String word) {
		Double weight = DictionaryUtils.getCommendatoryWords().get(word);
		if (null == weight) {
			weight = DictionaryUtils.getDerogratoryWords().get(word);
		}
		if (null == weight) {
			weight = DictionaryUtils.getNegativeWords().get(word);
		}
		if (null == weight) {
			weight = DictionaryUtils.getAdverbsWords().get(word);
		}
		if (null == weight) {
			weight = DictionaryUtils.getRhetoricalWords().get(word);
		}
		if (null == weight) {
			weight = DictionaryUtils.getInterjectionWords().get(word);
		}
		return null == weight ? 0 : weight;
	}
	
	/**
	 * 获取感叹词权重
	 * @return
	 */
	public static double getInterjectionWeight() {
		return 2;
	}
	
	public static void main(String[] args) throws Exception {
		String path = "D:\\resources\\data\\enter\\1.txt";
		String[] words = splitFile(path, SegUtils.getComplexSeg(), 2);
		ShowUtils.printToConsole(words);
		String str = "sssss";
		String regEx = "[0-9]";
		Pattern p = Pattern.compile(regEx);
		Matcher m = p.matcher(str);
		System.out.println(m.find());
		System.out.println(m.matches());
	}
}
