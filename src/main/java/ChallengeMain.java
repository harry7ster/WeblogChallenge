import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import scala.Tuple2;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by harry7ster on 2018-07-05.
 */
public class ChallengeMain {

    public static void main(String[] args){
        SparkConf conf = new SparkConf();
        conf.setMaster("local[*]");
        conf.setAppName("WeblogChallenger");

        SparkContext context = new SparkContext(conf);
        SparkSession ss = new SparkSession(context);
        Dataset<Row> df = ss.sqlContext().read()
                .format("com.databricks.spark.csv")
                .option("sep", " ")
                .load("src/main/resources/2015_07_22_mktplace_shop_web_log_sample.log");

        JavaRDD<CustomerDetails> updatedCustomerData = df.javaRDD().mapToPair(row -> {
            CustomerDetails cd = new CustomerDetails();
            SimpleDateFormat format = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));

            cd.setIp(row.get(2) == null ? "" : row.get(2).toString());
            cd.setEventDate(row.get(0).toString());
            cd.setBrowser(row.get(12) == null ? "" : row.get(12).toString());
            cd.setUrl(row.get(11) == null ? "" : row.get(11).toString());

           return new Tuple2<String, CustomerDetails>(row.get(2).toString() + row.get(12), cd);
        })
        .groupByKey()
        .flatMap(r -> {
            List<CustomerDetails> list = new ArrayList<CustomerDetails>();
            r._2().forEach(row -> {
                list.add(row);
            });
            Collections.sort(list, new CompareRow());
            int elementCount =0, sessionCount= 0;
            CustomerDetails refNode = null, prevNode = null;
            for(CustomerDetails row: list){
                if(elementCount ==0) {
                    refNode = row;
                    row.setSessionId(sessionCount + "");
                    row.setTimeTaken(0+"");
                }
                else{
                    SimpleDateFormat format = new SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                    format.setTimeZone(TimeZone.getTimeZone("UTC"));
                    long timeTaken = format.parse(row.getEventDate()).getTime() - format.parse(prevNode.getEventDate()).getTime();
                    if(((format.parse(row.getEventDate()).getTime() - format.parse(refNode.getEventDate()).getTime())/(1000*60.0)) > 15.0){ //if time between URL hits is more than 15 min, then create new session
                        row.setSessionId(++sessionCount + "");
                        row.setTimeTaken(0+"");
                        refNode = row;
                    }else {
                        row.setSessionId(sessionCount + "");
                        row.setTimeTaken(timeTaken+"");
                    }
                }
                prevNode = row;
                elementCount++;
            }
            return list.iterator();
        });

        ss.createDataFrame(updatedCustomerData, CustomerDetails.class).createOrReplaceTempView("SessionDetails");
        ss.sql("CACHE TABLE SessionDetails");
        ss.sql("select count(1) as Number_URL_Hits, count(distinct(url)) as Unique_URL_Hits, SUM(timeTaken)/60000 as Total_Time_For_Session from SessionDetails group by ip, browser, sessionId order by Total_Time_For_Session desc").show(false);
        ss.sql("select AVG(timeTaken)/60000 as Average_Session_Time from SessionDetails").show(false);
    }

    static class CompareRow implements Comparator<CustomerDetails>{

        public int compare(CustomerDetails a, CustomerDetails b) {
                return a.getEventDate().compareTo(b.getEventDate());
        }
    }
}
