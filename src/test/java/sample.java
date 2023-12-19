import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class sample {

    public static void main(String[] args) throws IOException {
        String file1 = "/Users//mph//Downloads//Survey_nudge - 05 Dec'23_07 Dec'23.csv";
        String file2 = "/Users//mph//Downloads//get_my_record - 05 Dec'23_07 Dec'23.csv";
//        String file3="";
        int count1= Integer.parseInt(getCount(file1));
        int count2= Integer.parseInt(getCount(file2));
//        int count3 = Integer.parseInt(getCount(file3));
        int totalCount = count1+count2;
        System.out.println(totalCount);
    }


    public static String getCount(String file) throws IOException {
        BufferedReader reader = null;
        String line = "",count =null;
        reader = new BufferedReader(new FileReader(file));
        while ((line = reader.readLine()) != null) {
            String[] row = line.split(",");
            for (int i = 0; i < row.length; i++) {
                if (row.length == 2) {
                    if (row[i].contains("Get my health records")) {
                        String[] value = row[i + 1].split("\\(");
                        count = value[0];
                    }
                } else {
                    if (row[i].contains("get_my_record"))
                        count =row[i + 2];
                }
            }
        }
        reader.close();

        return count;
    }
}

