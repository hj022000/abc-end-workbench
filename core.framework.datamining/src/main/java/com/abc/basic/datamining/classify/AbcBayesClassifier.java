package com.abc.basic.datamining.classify;

import com.abc.basic.algoritms.matrix.*;
import com.abc.basic.algoritms.matrix.Vector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;


public class AbcBayesClassifier <K extends Comparable<K>,V> extends AbstractDataMining{
    private static final Logger log = LoggerFactory.getLogger(AbcBayesClassifier.class);
    protected int[] classVec;
    //总数据集
    protected TreeSet<String> vocabList;
    public static void main(String[] args){
        AbcBayesClassifier bayesClassifier=new AbcBayesClassifier();
        bayesClassifier.train();
    }

    public  void readObject(ObjectMapper mapper,String json)throws JsonProcessingException,IOException{

        TrainResult desicTree = mapper.readValue(json, TrainResult.class);
        st=desicTree;
    }

    class TrainResult implements Serializable{
        double pAbusive;

        public Vector getP1Vect() {
            return p1Vect;
        }

        public void setP1Vect(Vector p1Vect) {
            this.p1Vect = p1Vect;
        }

        Vector p1Vect;
        Vector p0Vect;

        public Vector getP0Vect() {
            return p0Vect;
        }

        public void setP0Vect(Vector p0Vect) {
            this.p0Vect = p0Vect;
        }

        public TrainResult(){

        }
        public TrainResult(Vector p1Vect, Vector p0Vect, double pAbusive){
            this.p1Vect=p1Vect;
            this.p0Vect=p0Vect;
            this.pAbusive=pAbusive;
        }

        public double getpAbusive() {
            return pAbusive;
        }

        public void setpAbusive(double pAbusive) {
            this.pAbusive = pAbusive;
        }
    }
    @Override
    public TrainResult nativeTrain() {
        //装载分类信息
        createLabels();
        vocabList=createVocabList(dataSet);
        //创建向量
        Matrix matrix= buildTrainMatrix(vocabList);
        TrainResult trainResult=trainBayes(matrix);
        return trainResult;
    }

    public TrainResult trainBayes(Matrix trainMatrix){
        //分类向量
        Vector trainCategory=new Vector(classVec) ;
        log.debug("贝叶斯分类为:"+trainCategory);
        //训练数据集的条数
        int numTrainDocs=dataSet.size();
        //元素数量
        int numWords=vocabList.size();
        //训练数据集中概率
        double pAbusive = trainCategory.sum().doubleValue()/numTrainDocs;
        if(log.isInfoEnabled()) {
            log.info("绝对概率:" + pAbusive);
        }
        //#训练数据，对每篇训练文章,即每条数据集
        Vector p0Num=new Vector(numWords,1.0);
        Vector p1Num=new Vector(numWords,1.0);
        double p0Denom = 2.0;
        double p1Denom = 2.0;
        for(int i=0;i<numTrainDocs;i++){
            Vector pos=trainMatrix.getVector(i);
            if((Integer)trainCategory.get(i)==1){
                p1Num=p1Num.plus(pos);
                p1Denom = p1Num.sum().doubleValue();
                log.info("条件分类 p1Num:" + p1Num+" p1Denom:"+p1Denom);
            }else{
                //p0Num += pos#行加，32
                p0Num=p0Num.plus(pos);
                p0Denom = p0Num.sum().doubleValue();
                log.info("条件分类 p0Num:" + p0Num+" p0Denom:"+p0Denom);
            }
        }
        Vector p1=p1Num.divide(p1Denom);
        Vector p0=p0Num.divide(p0Denom);
        Vector p1Vect = p1.log();
        Vector p0Vect = p0.log();
        if(log.isInfoEnabled()) {
            log.info("训练数据分类结构 p1Vect:" + p0Vect
                    + " p1Vect:" + p1Vect + " pAbusive:" + pAbusive);
        }
        return new TrainResult(p1,p0,pAbusive);
    }

    protected Matrix buildTrainMatrix( TreeSet<String> vocabList){
        Matrix matrix=new Matrix(vocabList.size());
        for(int i=0;i<dataSet.size();i++){
            List list= (List) dataSet.get(i);
            Vector vector=setOfWords2Vec(vocabList, list);
            matrix.addVector(vector);
        }
        return matrix;
    }

    public TreeSet<String> createVocabList(List<List> dataSet){
        TreeSet<String> vocabList=new TreeSet<String>();
        if(CollectionUtils.isEmpty(dataSet)){
            throw new IllegalStateException("训练数据集为空");
        }
        for(List<String> list:dataSet ){
            for(String vocab:list){
                vocabList.add(vocab);
            }
        }
        return vocabList;
    }

    /**
     *
     * @param vocabList 全量字符集
     * @param inputSet 输入向量，每行数据
     */
    public Vector setOfWords2Vec(TreeSet<String> vocabList, List<String> inputSet){
        if(CollectionUtils.isEmpty(vocabList)||CollectionUtils.isEmpty(inputSet)){
            throw new IllegalStateException("全量字符集为空或者输入向量为空");
        }
        if(log.isDebugEnabled()){
            log.debug("输入向量为:"+inputSet);
        }
        Matrix matrix=new Matrix(1,vocabList.size(),0);
        Vector vector=matrix.getVector(0);
        String[] vocabArray = (String[]) vocabList.toArray(new String[0]);

        for(String word:inputSet){
            if(vocabList.contains(word)){
                int index=indexof(vocabArray,word);
                if(index >-1){
                    vector.put(index,1);
                }
            }
        }
        if(log.isDebugEnabled()){
            log.debug("创建向量为:"+vector);
        }
        return vector;
    }

    protected int indexof(String[] vocabArray,String label){
        if(ArrayUtils.isEmpty(vocabArray)){
            throw new IllegalStateException("全量字符集为空");
        }
        for(int i=0;i<vocabArray.length;i++){
            if(label.equals(vocabArray[i])){
                return i;
            }
        }
        return  -1;
    }
    @Override
    public void createDataSet() {
        String[][] postingList=new String[][]{{"my",    "dog",       "has",  "flea",    "problems",    "help",  "please"},//0-0
        {"maybe", "not",   "take",   "him",   "to",   "dog",     "park",   "stupid"},//1-1
        {"my",  "dalmation",  "is",    "so", "cute",    "I",    "love",     "him"},//2-0
        {"stop",  "posting",  "stupid",   "worthless",   "garbage"},//3-1
        {"mr",      "licks",       "ate",       "my",         "steak",
                "how",   "to",        "stop",     "him"},//4-0
                {"quit",   "buying",    "worthless", "dog",         "food",   "stupid"}//5-1
        };//6
        this.dataSet=new LinkedList();
        for(int i=0;i<postingList.length;i++){
            List<String> data=new ArrayList<String>();
            for(int j=0;j<postingList[i].length;j++){
                data.add(postingList[i][j]);
            }
            dataSet.add(data);
        }

    }

    /**
     * 训练数据文件名称
     * @return
     */
    @Override
    protected String setStoreTrainData(){
        return PATH_NAME+"bayes.txt";
    }

    /**
     * 训练结果数据存储
     * @return
     */
    protected String setStoreTrainResultName(){
        return PATH_NAME+"bayes-train-result.txt";
    }

    @Override
    public Map natvieClassify() {
        return null;
    }

    @Override
    public String[] createLabels() {
        classVec=new int[]{0,1,0,1,0,1};
        return labels;
    }
}