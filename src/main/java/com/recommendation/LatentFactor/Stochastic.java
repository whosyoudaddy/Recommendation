package com.recommendation.LatentFactor;

import Jama.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

@Component
public class Stochastic {

    private static Logger logger = LoggerFactory.getLogger(Stochastic.class);

    // 评分的范围
    private int rangeMin;
    private int rangeMax;

    //因子个数和循环次数
    private int factor;
    private int iteration;

    //用户数量和物品数量
    private int userNumber;
    private int itemNumber;

    //学习步长和抑制因子
    private double alpha;
    private double lambda;

    //用户的平均分矩阵和物品的平均分矩阵
    private double[] userAvg;
    private double[] itemAvg;

    //隐因子矩阵
    private double[][] user;
    private double[][] item;

    //训练集和测试集文件地址
    private String trainFilePath;
    private String testFilePath;

    //用户和物品隐因子矩阵
    private Matrix userMatrix;
    private Matrix itemMatrix;

    //用Map存放所有的评分记录
    private Map<Integer,LinkedHashMap<Integer,Double>> rates;

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    public void setFactor(int factor) {
        this.factor = factor;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public void setTrainFilePath(String trainFilePath) {
        this.trainFilePath = trainFilePath;
    }

    public void setTestFilePath(String testFilePath) {
        this.testFilePath = testFilePath;
    }

    public void setRangeMin(int rangeMin) {
        this.rangeMin = rangeMin;
    }

    public void setRangeMax(int rangeMax) {
        this.rangeMax = rangeMax;
    }

    //查找并设置最大用户和最大物品
    public void setUserNumberAndItemNumber() throws IOException {

        if(("").equals(trainFilePath)) {
            logger.error("train file not specified ");
            return;
        }

        if(("".equals(testFilePath))){
            logger.error("test file not specified ");
        }

        int maxUserId = 0;
        int maxitemId = 0;

        File trainFile = new File(trainFilePath);
        File testFile = new File(testFilePath);

        FileInputStream fis = new FileInputStream(trainFile);
        Scanner scanner = new Scanner(fis);
        while (scanner.hasNext()){
            int userId = scanner.nextInt();
            int itemId = scanner.nextInt();
            scanner.nextDouble();
            scanner.nextDouble();
            maxUserId = maxUserId>userId?maxUserId:userId;
            maxitemId = maxitemId>itemId?maxitemId:itemId;
        }
        fis = new FileInputStream(testFile);
        scanner = new Scanner(fis);
        while (scanner.hasNext()){
            int userId = scanner.nextInt();
            int itemId = scanner.nextInt();
            scanner.nextDouble();
            scanner.nextDouble();
            maxUserId = maxUserId>userId?maxUserId:userId;
            maxitemId = maxitemId>itemId?maxitemId:itemId;
        }
        userNumber = maxUserId+1;
        itemNumber = maxitemId+1;
        logger.info("max user id is {} and max item id is {}",maxUserId,maxitemId);
        if(fis!=null)
        fis.close();

    }

    //将训练集用Map的形式读入内存
    public void readIntoMemory() throws Exception {
        double[] userCount = new double[userNumber];
        double[] itemCount = new double[itemNumber];
        double totalCost = 0.0;
        int totalCount = 0;
        rates = new LinkedHashMap<>();
        FileInputStream fis = new FileInputStream(new File(trainFilePath));
        Scanner scanner = new Scanner(fis, "UTF-8");
        while (scanner.hasNext()) {
            int user_Id = scanner.nextInt();
            int item_Id = scanner.nextInt();
            double score = scanner.nextDouble();
            scanner.nextDouble();
            userAvg[user_Id]+=score;
            userCount[user_Id]++;
            itemAvg[item_Id]+=score;
            itemCount[item_Id]++;
            totalCost+=score;
            totalCount++;
            if(rates.containsKey(user_Id)){
                LinkedHashMap<Integer,Double> temp = rates.get(user_Id);
                temp.put(item_Id,score);
                rates.put(user_Id,temp);
            }else {
                LinkedHashMap<Integer,Double> tempMap = new LinkedHashMap<>();
                tempMap.put(item_Id,score);
                rates.put(user_Id,tempMap);
            }
        }

        totalCost/=totalCount;
        logger.info("the average cost is {}", totalCost);
        logger.info("start to calculate average rate for every user");
        for (int i = 0; i <userAvg.length ; i++) {
            if(userCount[i]!=0) {
                userAvg[i] /= userCount[i];
                userAvg[i] -= totalCost;
            }
        }
        logger.info("start to calculate average rate for every item");
        for (int i = 0; i <itemAvg.length ; i++) {
            if(itemCount[i]!=0){
                itemAvg[i]/=itemCount[i];
                itemAvg[i]-=totalCost;}
        }

    }

    //初始化变量
    public void init(){
        logger.info("start to set max User number and max item number");
        try {
            setUserNumberAndItemNumber();
        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.info("start to initializing and randomizing latent factor matrix");

        userAvg = new double[userNumber];
        itemAvg = new double[itemNumber];

        user = new double[factor][userNumber];
        item = new double[factor][itemNumber];

        Random random = new Random();
        double time = 1/Math.sqrt(factor);
        for (int i = 0; i <factor ; i++) {
            for (int j = 0; j <userNumber ; j++) {
                user[i][j] = random.nextDouble()*time;
            }
        }
        for (int i = 0; i <factor ; i++) {
            for (int j = 0; j <itemNumber ; j++) {
                item[i][j] = random.nextDouble()*time;
            }
        }

        logger.info("start to read data into memory");
        try {
            readIntoMemory();
        } catch (Exception e) {
            e.printStackTrace();
        }

        logger.info("finish init \n");
    }

    //针对某一条数据进行随机梯度下降
    public void stochasticGradientDescent(int userId, int itemId, double score){
        userMatrix = new Matrix(user);
        itemMatrix = new Matrix(item);

        Matrix matrix1 = userMatrix.getMatrix(0,factor-1,userId,userId);
        Matrix matrix2 = itemMatrix.getMatrix(0,factor-1,itemId,itemId) ;

        Matrix ans = matrix1.transpose().times(matrix2);
        double[][] temp = ans.getArray();

        for (int i = 0; i <factor ; i++) {
            user[i][userId] = user[i][userId] - alpha*((temp[0][0]-score+userAvg[userId]+itemAvg[itemId])*item[i][itemId]+lambda*user[i][userId]);
            item[i][itemId] = item[i][itemId] -  alpha*((temp[0][0]-score+userAvg[userId]+itemAvg[itemId])*user[i][userId]+lambda*item[i][itemId]);
        }
        userAvg[userId]-=alpha*((temp[0][0]-score+userAvg[userId]+itemAvg[itemId])+lambda*userAvg[userId]);
        itemAvg[itemId]-=alpha*((temp[0][0]-score+userAvg[userId]+itemAvg[itemId])+lambda*itemAvg[itemId]);

        userMatrix = new Matrix(user);
        itemMatrix = new Matrix(item);
    }

    //计算损失函数
    public double costFunction(){
        int count = 0;
        double totalCost = 0.0;
        logger.info("start to calculate the totalCost");
        for (Map.Entry<Integer,LinkedHashMap<Integer,Double>> map:rates.entrySet()) {
            int userId = map.getKey();
            for (HashMap.Entry<Integer,Double> entry:map.getValue().entrySet()) {
                int itemId = entry.getKey();
                double score = entry.getValue();
                Matrix matrix1 = userMatrix.getMatrix(0,factor-1,userId,userId);
                Matrix matrix2 = itemMatrix.getMatrix(0,factor-1,itemId,itemId) ;
                Matrix ans = matrix1.transpose().times(matrix2);
                double[][] temp = ans.getArray();
                totalCost+=temp[0][0];
                count++;
            }
        }
        return totalCost/count;
    }

    //进行预测
    public double predict() throws FileNotFoundException {
        logger.info("start to calculate prediction ");
        int count = 0;
        double RMSE = 0.0;
        File testFile = new File(testFilePath);
        FileInputStream fis = new FileInputStream(testFile);
        Scanner scanner = new Scanner(fis);
        while (scanner.hasNext()){
            count++;
            int userId = scanner.nextInt();
            int itemId = scanner.nextInt();
            double score = scanner.nextDouble();
            scanner.nextDouble();
            double predict = 0;
            for (int i = 0; i < factor; i++) {
                predict += (user[i][userId] * item[i][itemId]) ;
            }
            predict+= userAvg[userId] + itemAvg[itemId];
            if(predict<rangeMin){
                predict = rangeMin;
            }
            if(predict>rangeMax){
                predict = rangeMax;
            }
            RMSE+=Math.pow((predict-score),2);
        }

        return Math.sqrt(RMSE/count);
    }

    public void doGradientDescent(){
        if(iteration==0){
            logger.error("iteration not initialized ");
        }
        if(factor==0){
            logger.error("factor not initialized ");
        }
        logger.info("start to do gradient descent ");
        for (int k = 0; k <iteration ; k++) {
            logger.info("this is the {} iterations",k+1);
            for (Map.Entry<Integer,LinkedHashMap<Integer,Double>> map:rates.entrySet()) {
                int userId = map.getKey();
                for (HashMap.Entry<Integer,Double> entry:map.getValue().entrySet()) {
                    int itemId = entry.getKey();
                    double score = entry.getValue();
                    stochasticGradientDescent(userId,itemId,score);
                }
            }
            logger.info("finish gradient descent");
            double cost = costFunction();
            logger.info("for this iteration, the cost is {}",cost);
            double predicted = 0;
            try {
                predicted = predict();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            logger.info("for this iteration, the predicted RMSE is {} \n",predicted);
        }
    }

//    public static void main(String[] args) {
//        Stochastic test = new Stochastic();
//        test.setAlpha(0.003);
//        test.setLambda(0.01);
//        test.setIteration(30);
//        test.setFactor(220);
//        test.setRangeMin(1);
//        test.setRangeMax(5);
//        test.setTrainFilePath("/Users/victor/Desktop/ml-latest-small/train.txt");
//        test.setTestFilePath("/Users/victor/Desktop/ml-latest-small/test.txt");
//        test.init();
//        test.doGradientDescent();
//    }
}
