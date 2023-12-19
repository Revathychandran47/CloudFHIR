import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.opencsv.exceptions.CsvValidationException;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.json.JSONException;
//import org.json.JSONObject;
import org.json.simple.JSONObject;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.*;
import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import com.opencsv.CSVReader;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class utils {
     static RequestSpecification requestSpecification;
     static ResponseSpecification responseSpecification;
     static Response response;
     static RequestSpecification req;
     static APIResources resourceAPI;
    static String JDBC_DRIVER= "org.postgresql.Driver";
    static String DB_URL="jdbc:postgresql://mpowered-rds-qa.c3mwkdwpqglf.us-east-1.rds.amazonaws.com:5432/mpowered";
    //  Database credentials
    static  String USER = "mpowered";
    static  String PASS = "mpowered-server";

    //To read values from global.properties file
    public static String getGlobalValue(String key)  {
        Properties prop = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream("src//main//resources//global.properties");
            prop.load(fis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return prop.getProperty(key);
    }

    //To read values from global.properties file
    public static int getCount(String jsonFile) throws IOException {
        String jsonTemplate = FileUtils.readFileToString(new File(jsonFile));
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonTemplate).getAsJsonObject();

        int count=0;
        // Iterate over key-value pairs using entrySet()
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String value = String.valueOf(entry.getValue());
            if(value.contains("<")&&(value.contains(">"))){
                Pattern pattern = Pattern.compile(".*[<>].*");
                Matcher matcher = pattern.matcher(value);
                while (matcher.find()) {
                    count++;
                }
            }
        }

        return count*2;
    }


    //To read values from csv file
    public static ArrayList<String> getValueFromFile(String file) throws Exception
    {
        ArrayList<String> arrayList = new ArrayList<>();

        String fileValues = FileUtils.readFileToString(new File(file));
        String[] values = fileValues.split(",");
        for(String value : values)
        {
            if(!(value == null))
            {
                value = value.trim();
                arrayList.add(value);
            }
        }
        return arrayList;

    }

    //To replace <string> with actual value in json template
    public static String updateJson(String template, String key, String value)
    {
        return template.replace(key, value);
    }

    //Posting data into smileCDR
    public static Response insertPatientDataIntoSmile(String json) throws IOException {
        requestSpecification = given().spec(requestSpecification("smileCDR")).body(json).pathParam("resourceName","Patient");

        APIResources resourceAPI = APIResources.valueOf("AddAPI");
        responseSpecification = new ResponseSpecBuilder().expectStatusCode(201).expectContentType(ContentType.JSON).build();
        response = requestSpecification.when().post(resourceAPI.getResource());
        assertEquals(response.getStatusCode(),201);
        return response;
    }

    //Request specification for making API calls
    public static RequestSpecification requestSpecification(String serviceName) throws IOException {
        //Logging.txt will overwrite each time so inorder to avoid that
        if(req==null){
            PrintStream log = new PrintStream(new FileOutputStream("logging.txt"));
            if(serviceName.equalsIgnoreCase("smileCDR")) {
                req = new RequestSpecBuilder().setBaseUri(getGlobalValue("smileCDRBaseUrl"))
                        .addFilter(RequestLoggingFilter.logRequestTo(log))
                        .addFilter(ResponseLoggingFilter.logResponseTo(log)).
                        setContentType(ContentType.JSON).build();
            }
            else{
                req = new RequestSpecBuilder().setBaseUri(getGlobalValue("azureBaseUrl"))
                        .addFilter(RequestLoggingFilter.logRequestTo(log))
                        .addFilter(ResponseLoggingFilter.logResponseTo(log)).
                        setContentType(ContentType.JSON).build();
            }
            return req;}
        return req;
    }

    //To get value from response json
    public static String getJsonPath(Response response, String key){
        String resp = response.asString();
        JsonPath js = new JsonPath(resp);
        return js.get(key).toString();
    }

    //Making api call to smileCDR to read data
    public static Response readPatientDataFromSmile(String resourceName, String resourceID) throws IOException {
        requestSpecification = given().spec(requestSpecification("smileCDR")).pathParam("resourceName",resourceName).pathParam("id",resourceID);
        resourceAPI = APIResources.valueOf("GetAPI");
        responseSpecification = new ResponseSpecBuilder().expectStatusCode(200).expectContentType(ContentType.JSON).build();
        response = requestSpecification.when().get(resourceAPI.getResource());
        assertEquals(response.getStatusCode(),200);
        return response;
    }

    //Writing data to a file
    public static void writeToFile(String fileName, String json) throws IOException {
        FileWriter file = new FileWriter(fileName,true);
        BufferedWriter bw = new BufferedWriter(file);
        bw.append(json).append("\n");
        bw.close();
        file.close();
    }


    public static Response readDataFromAzure(String resourceNameID) throws IOException {
        //Reading access token
        response= given().spec(requestSpecification("azure"))
                .header("Content-Type", "application/x-www-form-urlencoded").
                formParam("grant_type","Client_Credentials").
                formParam("client_id","81848d30-b606-4183-9e7f-4622ccfb6796").
                formParam("client_secret","B6F8Q~9oG~i9LQwbkvbcksoqaFf9_iMjMoVS4c2A").
                formParam("resource","https://cloudfhir-cloudfhirservice.fhir.azurehealthcareapis.com").
                formParam("scope","https%3A%2F%2Fgraph.microsoft.com%2F.default").
                when().post("https://login.microsoftonline.com/d3bd044e-5719-4125-8fc0-732ef8c5a28c/oauth2/token");

        //Fetch access token
        String accessToken = getJsonPath(response,"access_token");

        //Read data from azure using the token
        response= given().spec(new RequestSpecBuilder().setBaseUri(getGlobalValue("azureBaseUrl")).
                setContentType(ContentType.JSON).build()).
                header("Authorization","Bearer "+accessToken).
                when().get("https://cloudfhir-cloudfhirservice.fhir.azurehealthcareapis.com"+"/"+resourceNameID);

        return response;

    }

    public static ArrayList<JSONObject> getResourceValues(String fileName) {
        ArrayList<JSONObject> json=new ArrayList<>();
        JSONObject obj = null;

        // This will reference one line at a time
        String line = null;

        try {
            // FileReader reads text files in the default encoding.
            FileReader fileReader = new FileReader(getGlobalValue(fileName));

            // Always wrap FileReader in BufferedReader.
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            while((line = bufferedReader.readLine()) != null) {
                obj = (JSONObject) new JSONParser().parse(line);
                json.add(obj);
            }
            // Always close files.
            bufferedReader.close();
        }
        catch(IOException | ParseException ex) {
           ex.printStackTrace();
        }

        return json;
    }

    public static String getIDFromDB(String source) throws ClassNotFoundException, SQLException {
        Connection conn = null;
        Statement stmt = null;
        String destinationRef = null;
        //STEP 2: Register JDBC driver
        Class.forName("org.postgresql.Driver");

        //STEP 3: Open a connection
        System.out.println("Connecting to db....");
        conn = DriverManager.getConnection(DB_URL,USER,PASS);

        //STEP 4: Execute a query
        System.out.println("Creating statement…");
        stmt = conn.createStatement();
        String sql;
        sql = "SELECT source_reference,destination_reference FROM data_migration.reference_change where source_reference ="+"'"+source+"';";
        String sql1=sql.toLowerCase();
        ResultSet rs = null;
        if (sql1.contains("where")) {
            rs = stmt.executeQuery(sql);
        }
        while(rs.next()) {

            //Retrieve by column name
            destinationRef = rs.getString("destination_reference");
            
        }

            rs.close();
            stmt.close();
            conn.close();

        return destinationRef;
    }

    public static List<String> getValueFromCSV(String columnNameValue) {
        List<String> columnValues = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(getGlobalValue("inputFile")));
            CSVParser parser = CSVFormat.DEFAULT.withDelimiter(',').withHeader().parse(br);
            for (CSVRecord record : parser) {
                columnValues.add(record.get(columnNameValue));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return columnValues;
    }

    public static List<String> getColumnNames() {
        List<String> columnNames = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(getGlobalValue("inputFile")))) {
            String[] header = reader.readNext(); // Read the header row

            if (header != null) {
                // Print the header names
                columnNames.addAll(Arrays.asList(header));
            } else {
                System.out.println("Error: CSV file is empty or has no header.");
            }
        } catch (IOException | CsvValidationException e) {
            e.printStackTrace();
        }
        return columnNames;
    }


}
