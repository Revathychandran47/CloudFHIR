import org.json.simple.JSONObject;
import java.io.File;
import java.util.ArrayList;

public class readDataFromAzure extends utils {
    private static String jsonTemplate = getGlobalValue("jsonTemplates")+ File.separator+getGlobalValue("resource").toLowerCase()+".json";
    public static void main(String[] args) throws Exception {

        //Get count of json to be generated by reading values having <> in template
        int count = getCount(jsonTemplate);

        for(int i = 0; i<1; i++){
            //Getting data from smileOutput file and storing as arraylist
            ArrayList<JSONObject> smileData= getResourceValues("smileOutput");

            //Retrieving smileID and resourceType for each record
//            String smileID= smileData.get(i).get("id").toString();
//            String resourceName= smileData.get(i).get("resourceType").toString();
            String smileID="26182328";
            String resourceName="Procedure";
            System.out.println("SmileID: " + smileID+" "+resourceName);

            smileResponse= readDataFromSmile(resourceName,smileID);

            //Creating sourceReference to fetch data from DB
            String sourceReference= resourceName+"/"+smileID;

            //Fetch the corresponding azure ID From DB
//            String azureID= getIDFromDB(sourceReference);
            String azureID = "Procedure/755af3bd-7c29-4cd9-b80b-92f788a2ead5";
            System.out.println("AzureID: "+azureID);

            //Reading patient data from azure
            azureResponse= readDataFromAzure(azureID);

            //Verifying data
            dataValidation.verifyElement(i, azureResponse, smileResponse);
            dataValidation.verifyExtensions(i, azureResponse);

            //Writing response to a file
            writeToFile(getGlobalValue("azureOutput"),azureResponse.asString());
        }

    }

}
