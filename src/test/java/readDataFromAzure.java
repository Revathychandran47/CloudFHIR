import io.restassured.response.Response;
import org.json.JSONException;
import org.json.simple.JSONObject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

public class readDataFromAzure extends utils {
    private static String jsonTemplate = getGlobalValue("jsonTemplate");
    public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException, JSONException {

        //Getting data from smileOutput file and storing as arraylist
        ArrayList<JSONObject> smileData= getResourceValues("smileOutput");

        System.out.println("smileData "+smileData);

        //Get count of json to be generated by reading values having <> in template
        int count = getCount(jsonTemplate);
        System.out.println("count: " +getCount(jsonTemplate) );

        for(int i = 0; i< 2; i++){

            //Retrieving smileID and resourceType for each record
            String smileID= smileData.get(i).get("id").toString();
            String resourceName= smileData.get(i).get("resourceType").toString();

            smileResponse= readDataFromSmile(resourceName,smileID);



            //Creating sourceReference to fetch data from DB
//            String sourceReference= resourceName+"/"+smileID;
            String sourceReference= "Patient/26127256";

            //Fetch the corresponding azure ID From DB
        String azureID= getIDFromDB(sourceReference);
        System.out.println(azureID);

        //Reading patient data from azure
        azureResponse= readDataFromAzure(azureID);
            verifyElement(i, azureResponse, smileResponse);

        //Writing response to a file
//        writeToFile(getGlobalValue("azureOutput"),response.asString());
        }

    }

}
