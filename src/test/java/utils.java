import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
//import org.json.JSONObject;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.json.JSONArray;
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
import org.junit.Assert;

public class utils {
    private static String jsonTemplate = getGlobalValue("jsonTemplate");

    static RequestSpecification requestSpecification;
    static ResponseSpecification responseSpecification;
    static Response response;
    static Response azureResponse;
    static Response smileResponse;
    static RequestSpecification req;
    static APIResources resourceAPI;
    static String JDBC_DRIVER = "org.postgresql.Driver";
    static String DB_URL = "jdbc:postgresql://mpowered-rds-qa.c3mwkdwpqglf.us-east-1.rds.amazonaws.com:5432/mpowered";

    //  Database credentials
    static String USER = "mpowered";
    static String PASS = "mpowered-server";

    static String fullAddress = "";
    static String email = "";
    static String phone = "";
    static String preferredName = "";
    static String previousName = "";
    static String ssnValue = "";
    static String maritalStatusValue = "";
    static String languageValue = "";
    static String dataSourceValue = "";
    static String categoryValue = "";
    static String procedureNameValue = "";

    static  Patient patient;
    static Procedure procedure;

    //To read values from global.properties file
    public static String getGlobalValue(String key) {
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

        int count = 0;
        // Iterate over key-value pairs using entrySet()
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String value = String.valueOf(entry.getValue());
            if (value.contains("<") && (value.contains(">"))) {
                Pattern pattern = Pattern.compile(".*[<>].*");
                Matcher matcher = pattern.matcher(value);
                while (matcher.find()) {
                    count++;
                }
            }
        }

        return count * 2;
    }


    //To read values from csv file
    public static ArrayList<String> getValueFromFile(String file) throws Exception {
        ArrayList<String> arrayList = new ArrayList<>();

        String fileValues = FileUtils.readFileToString(new File(file));
        String[] values = fileValues.split(",");
        for (String value : values) {
            if (!(value == null)) {
                value = value.trim();
                arrayList.add(value);
            }
        }
        return arrayList;

    }

    //To replace <string> with actual value in json template
    public static String updateJson(String template, String key, String value) {
        return template.replace(key, value);
    }

    //Posting data into smileCDR
    public static Response insertPatientDataIntoSmile(String json) throws IOException {
        requestSpecification = given().spec(requestSpecification("smileCDR")).body(json).pathParam("resourceName", "Patient");

        APIResources resourceAPI = APIResources.valueOf("AddAPI");
        responseSpecification = new ResponseSpecBuilder().expectStatusCode(201).expectContentType(ContentType.JSON).build();
        response = requestSpecification.when().post(resourceAPI.getResource());
        assertEquals(response.getStatusCode(), 201);
        return response;
    }

    //Request specification for making API calls
    public static RequestSpecification requestSpecification(String serviceName) throws IOException {
        //Logging.txt will overwrite each time so inorder to avoid that
        if (req == null) {
            PrintStream log = new PrintStream(new FileOutputStream("logging.txt"));
            if (serviceName.equalsIgnoreCase("smileCDR")) {
                req = new RequestSpecBuilder().setBaseUri(getGlobalValue("smileCDRBaseUrl"))
                        .addFilter(RequestLoggingFilter.logRequestTo(log))
                        .addFilter(ResponseLoggingFilter.logResponseTo(log)).
                        setContentType(ContentType.JSON).build();
            } else {
                req = new RequestSpecBuilder().setBaseUri(getGlobalValue("azureBaseUrl"))
                        .addFilter(RequestLoggingFilter.logRequestTo(log))
                        .addFilter(ResponseLoggingFilter.logResponseTo(log)).
                        setContentType(ContentType.JSON).build();
            }
            return req;
        }
        return req;
    }

    //To get value from response json
    public static String getJsonPath(Response response, String key) {
        String resp = response.asString();
        JsonPath js = new JsonPath(resp);
        return js.get(key).toString();
    }

    //Making api call to smileCDR to read data
    public static Response readPatientDataFromSmile(String resourceName, String resourceID) throws IOException {
        requestSpecification = given().spec(requestSpecification("smileCDR")).pathParam("resourceName", resourceName).pathParam("id", resourceID);
        resourceAPI = APIResources.valueOf("GetAPI");
        responseSpecification = new ResponseSpecBuilder().expectStatusCode(200).expectContentType(ContentType.JSON).build();
        response = requestSpecification.when().get(resourceAPI.getResource());
        assertEquals(response.getStatusCode(), 200);
        return response;
    }

    //Writing data to a file
    public static void writeToFile(String fileName, String json) throws IOException {
        FileWriter file = new FileWriter(fileName, true);
        BufferedWriter bw = new BufferedWriter(file);
        bw.append(json).append("\n");
        bw.close();
        file.close();
    }


    public static Response readDataFromAzure(String resourceNameID) throws IOException {
        //Reading access token
        response = given().spec(requestSpecification("azure"))
                .header("Content-Type", "application/x-www-form-urlencoded").
                formParam("grant_type", "Client_Credentials").
                formParam("client_id", "81848d30-b606-4183-9e7f-4622ccfb6796").
                formParam("client_secret", "B6F8Q~9oG~i9LQwbkvbcksoqaFf9_iMjMoVS4c2A").
                formParam("resource", "https://cloudfhir-cloudfhirservice.fhir.azurehealthcareapis.com").
                formParam("scope", "https%3A%2F%2Fgraph.microsoft.com%2F.default").
                when().post("https://login.microsoftonline.com/d3bd044e-5719-4125-8fc0-732ef8c5a28c/oauth2/token");

        //Fetch access token
        String accessToken = getJsonPath(response, "access_token");

        response = given().spec(new RequestSpecBuilder().setBaseUri(getGlobalValue("azureBaseUrl")).
                        setContentType(ContentType.JSON).build()).

                header("Authorization", "Bearer " + accessToken).
                when().get("https://cloudfhir-cloudfhirservice.fhir.azurehealthcareapis.com" + "/" + resourceNameID);


        return response;

    }


    public static Response readDataFromSmile(String resourceName, String smileID) throws IOException {
        //Read data from azure using the token
        response = given().spec(new RequestSpecBuilder().setBaseUri(getGlobalValue("azureBaseUrl")).
                        setContentType(ContentType.JSON).build()).
                when().get("https://qa-fhir.mpowered-health.com/fhir/DEFAULT/" + resourceName + "/" + smileID);

        return response;

    }

    public static ArrayList<JSONObject> getResourceValues(String fileName) {
        ArrayList<JSONObject> json = new ArrayList<>();
        JSONObject obj = null;

        // This will reference one line at a time
        String line = null;

        try {
            // FileReader reads text files in the default encoding.
            FileReader fileReader = new FileReader(getGlobalValue(fileName));

            // Always wrap FileReader in BufferedReader.
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            while ((line = bufferedReader.readLine()) != null) {
                obj = (JSONObject) new JSONParser().parse(line);
                json.add(obj);
            }
            // Always close files.
            bufferedReader.close();
        } catch (IOException | ParseException ex) {
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
        conn = DriverManager.getConnection(DB_URL, USER, PASS);

        //STEP 4: Execute a query
        System.out.println("Creating statement…");
        stmt = conn.createStatement();
        String sql;
        sql = "SELECT source_reference,destination_reference FROM data_migration.reference_change where source_reference =" + "'" + source + "';";
        String sql1 = sql.toLowerCase();
        ResultSet rs = null;
        if (sql1.contains("where")) {
            rs = stmt.executeQuery(sql);
        }
        while (rs.next()) {

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

    public static void verifyElement(int i, Response azureResponse, Response smileResponse) throws IOException {
        ArrayList<JSONObject> smileData = getResourceValues("smileOutput");
        JSONObject jsonObj = smileData.get(i);
        Set<String> keys = jsonObj.keySet();
        System.out.println(keys);
        String nodeElement = null;
        for (int j = i; j == i; j++) {
            for (String item : keys) {
                if (item.equalsIgnoreCase("meta") || item.equalsIgnoreCase("extension") || item.equalsIgnoreCase("id")) {
                    continue;
                }
                JsonPath j1 = azureResponse.jsonPath();
                System.out.println("item.........." + item);
                System.out.println("Azure " + j1.get(item).toString());
                String azureNodeElement = j1.get(item).toString();

                JsonPath j2 = smileResponse.jsonPath();
                nodeElement = j2.get(item).toString();
                System.out.println("nodeElement: " + nodeElement);
                Assert.assertEquals(nodeElement, azureNodeElement);
            }
        }
    }

    //reading extension from Azure
    public static void readExtensionAndCompare(int i, Response azureResponse, Response smileResponse, String resource,  String urlValue) throws JsonProcessingException {


        for (int j = i; j == i; j++) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(azureResponse.asString());
            ObjectMapper objectMapper1 = new ObjectMapper();
            JsonNode smileJsonNode = objectMapper1.readTree(smileResponse.asString());

            // Find the object with the "url" equal to "urlValue"
            JsonNode urlExtension = null;
            for (JsonNode extension : jsonNode.path("extension")) {
                if (urlValue.equals(extension.path("url").asText())) {
                    urlExtension = extension;
                    break;
                }
            }

            // Get the valueString from the "urlValue" extension
            String extensionValueString = urlExtension.path("valueString").asText();
            System.out.println("Value for extension with URL " + urlValue + ": " + extensionValueString);
            String azureEXtensionValueString = extensionValueString.replaceAll("[^a-zA-Z0-9:@.]", "").toLowerCase();
            System.out.println("azureEXtensionValueString: " + azureEXtensionValueString);


            if (resource.equalsIgnoreCase("patient")) {
                String smileName = null;
                if (urlValue.equals("name")) {
//                    ObjectMapper objectMapper1 = new ObjectMapper();
//                    JsonNode jsonNodeName = objectMapper1.readTree(smileResponse.asString());
                    IParser parser = FhirContext.forR4().newJsonParser();
                    Patient patient = parser.parseResource(Patient.class, objectMapper.writeValueAsString(smileJsonNode));
                    System.out.println(patient.getName().get(0).getGiven());

                    JsonNode nameNode = jsonNode.path("name");
                    if (nameNode.isArray() && !nameNode.isEmpty()) {
//                    String smileName= nameNode.get(0).path("given").asText().concat(nameNode.get(0).path("family").asText());
//                    String givenName = nameNode.get(0).path("given").get(0).asText();
                        String givenName = patient.getName().get(0).getGiven().toString().replaceAll("[^a-zA-Z0-9,|:\\s]", "");
                        String familyName = nameNode.get(0).path("family").asText();
                        String fullName = givenName + " " + familyName;
                        smileName = fullName.replace("|", " ").trim().toLowerCase();
                        System.out.println("smileName: " + smileName);
                    }

                    Assert.assertEquals(azureEXtensionValueString, smileName);
                }

                if (urlValue.equals("address")) {
//                    ObjectMapper objectMapper1 = new ObjectMapper();
//                    JsonNode smileJsonNode = objectMapper1.readTree(smileResponse.asString());
                    IParser parser = FhirContext.forR4().newJsonParser();
                    Patient patient = parser.parseResource(Patient.class, objectMapper.writeValueAsString(smileJsonNode));
                    for (int l = 0; l < patient.getAddress().size(); l++) {
                        if (patient.getAddress().get(l).getUse() != null) {
                            fullAddress = fullAddress + "use:" + patient.getAddress().get(l).getUse();
                        }
                        if (patient.getAddress().get(l).getLine() != null) {
                            fullAddress = fullAddress + "line:" + patient.getAddress().get(l).getLine();
                        }
                        if (patient.getAddress().get(l).getCity() != null) {
                            fullAddress = fullAddress + "city:" + patient.getAddress().get(l).getCity();
                        }
                        if (patient.getAddress().get(l).getDistrict() != null) {
                            fullAddress = fullAddress + "district:" + patient.getAddress().get(l).getDistrict();
                        }
                        if (patient.getAddress().get(l).getState() != null) {
                            fullAddress = fullAddress + "state:" + patient.getAddress().get(l).getState();
                        }
                        if (patient.getAddress().get(l).getPostalCode() != null) {
                            fullAddress = fullAddress + "postalCode:" + patient.getAddress().get(l).getPostalCode();
                        }
                        if (patient.getAddress().get(l).getCountry() != null) {
                            fullAddress = fullAddress + "country:" + patient.getAddress().get(l).getCountry();
                        }
                    }
                    fullAddress = fullAddress.replaceAll("[^a-zA-Z0-9:]", "").toLowerCase();
                    System.out.println("fullAddress: " + fullAddress.replaceAll("[^a-zA-Z0-9:]", ""));
                    Assert.assertEquals(azureEXtensionValueString, fullAddress);
                }


                if (urlValue.equals("email")) {
//                    ObjectMapper objectMapper1 = new ObjectMapper();
//                    JsonNode jsonNodeName = objectMapper1.readTree(smileResponse.asString());
                    IParser parser = FhirContext.forR4().newJsonParser();
                    Patient patient = parser.parseResource(Patient.class, objectMapper.writeValueAsString(smileJsonNode));
                    for (int l = 0; l < patient.getTelecom().size(); l++) {
                        if (patient.getTelecom().get(l).getSystem().toString().equalsIgnoreCase("email")) {
                            email = email + "email:" + patient.getTelecom().get(l).getValue();
                            if (patient.getTelecom().get(l).getRank() == 1) {
                                email = email + "primary:true";
                            }

                        }

                    }
                    System.out.println("smileEmailValue: " + email);
                    Assert.assertEquals(azureEXtensionValueString, email);
                }


                if (urlValue.equals("phone")) {
//                    ObjectMapper objectMapper1 = new ObjectMapper();
//                    JsonNode jsonNodeName = objectMapper1.readTree(smileResponse.asString());
                    IParser parser = FhirContext.forR4().newJsonParser();
                    patient = parser.parseResource(Patient.class, objectMapper.writeValueAsString(smileJsonNode));
                    for (int l = 0; l < patient.getTelecom().size(); l++) {
                        if (patient.getTelecom().get(l).getSystem().toString().equalsIgnoreCase("phone")) {
                            System.out.println("smilephone Value: " + patient.getTelecom().get(l).getValue());
                            phone = phone + "phone:" + patient.getTelecom().get(l).getValue();
                            if (patient.getTelecom().get(l).getRank() == 1) {
                                phone = phone + "primary:true";
                            }

                        }

                    }
                    System.out.println("smilePhoneValue: " + phone);
                    Assert.assertEquals(azureEXtensionValueString, phone);
                }


                if (urlValue.equals("preferredName")) {
//                    ObjectMapper objectMapper1 = new ObjectMapper();
//                    JsonNode jsonNodeName = objectMapper1.readTree(smileResponse.asString());
                    IParser parser = FhirContext.forR4().newJsonParser();
                    Patient patient = parser.parseResource(Patient.class, objectMapper.writeValueAsString(smileJsonNode));
                    for (int l = 0; l < patient.getName().size(); l++) {
                        if (patient.getName().get(l).getUse().toString().equalsIgnoreCase("usual")) {
                            System.out.println("smile preferredName Value: " + patient.getName().get(l).getText());
                            preferredName = patient.getName().get(l).getText().toLowerCase();

                        }

                    }
                    System.out.println("smilePreferredNameValue: " + preferredName);
                    Assert.assertEquals(azureEXtensionValueString, preferredName);
                }


                if (urlValue.equals("previousName")) {
//                    ObjectMapper objectMapper1 = new ObjectMapper();
//                    JsonNode jsonNodeName = objectMapper1.readTree(smileResponse.asString());
                    IParser parser = FhirContext.forR4().newJsonParser();
                    patient = parser.parseResource(Patient.class, objectMapper.writeValueAsString(smileJsonNode));
                    for (int l = 0; l < patient.getName().size(); l++) {
                        if (patient.getName().get(l).getUse().toString().equalsIgnoreCase("old")) {
                            System.out.println("smile previousName Value: " + patient.getName().get(l).getText());
                            previousName = patient.getName().get(l).getText().toLowerCase();
                            break;
                        }

                    }
                    System.out.println("smilePreviousNameValue: " + previousName);
                    Assert.assertEquals(azureEXtensionValueString, previousName);
                }

                if (urlValue.equals("socialSecurityNumber")) {
//                    ObjectMapper objectMapper1 = new ObjectMapper();
//                    JsonNode jsonNodeName = objectMapper1.readTree(smileResponse.asString());
                    IParser parser = FhirContext.forR4().newJsonParser();
                    patient = parser.parseResource(Patient.class, objectMapper.writeValueAsString(smileJsonNode));
                    for (int l = 0; l < patient.getIdentifier().size(); l++) {
                        if (patient.getIdentifier().get(l).getSystem().equalsIgnoreCase("http://hl7.org/fhir/sid/us-ssn")) {
                            System.out.println("smile ssn identifier Value: " + patient.getIdentifier().get(l).getValue());
                            ssnValue = patient.getIdentifier().get(l).getValue().toLowerCase();
                            break;
                        }

                    }
                    System.out.println("smileSsnValue: " + ssnValue);
                    Assert.assertEquals(azureEXtensionValueString, ssnValue);
                }


                if (urlValue.equals("maritalStatus")) {
//                    ObjectMapper objectMapper1 = new ObjectMapper();
//                    JsonNode jsonNodeName = objectMapper1.readTree(smileResponse.asString());
                    IParser parser = FhirContext.forR4().newJsonParser();
                    patient = parser.parseResource(Patient.class, objectMapper.writeValueAsString(smileJsonNode));
                    for (int l = 0; l < patient.getMaritalStatus().getCoding().size(); l++) {
                        if (patient.getMaritalStatus().getCoding().get(l).getDisplay() != null) {
                            System.out.println("smile maritalStatus display Value: " + patient.getMaritalStatus().getCoding().get(l).getDisplay());
                            maritalStatusValue = patient.getMaritalStatus().getCoding().get(l).getDisplay().toLowerCase();
                            break;
                        } else {
                            System.out.println("smile maritalStatus text Value: " + patient.getMaritalStatus().getText());
                            maritalStatusValue = patient.getMaritalStatus().getText().toLowerCase();
                            break;
                        }


                    }
                    System.out.println("smileMaritalStatusValueValue: " + maritalStatusValue);
                    Assert.assertEquals(azureEXtensionValueString, maritalStatusValue);
                }


                if (urlValue.equals("language")) {
//                    ObjectMapper objectMapper1 = new ObjectMapper();
//                    JsonNode jsonNodeName = objectMapper1.readTree(smileResponse.asString());
                    IParser parser = FhirContext.forR4().newJsonParser();
                    patient = parser.parseResource(Patient.class, objectMapper.writeValueAsString(smileJsonNode));
                    for (int l = 0; l < patient.getCommunication().get(l).getLanguage().getCoding().size(); l++) {
                        if (patient.getCommunication().get(l).getLanguage().getCoding().get(l).getDisplay() != null) {
                            System.out.println("smile language display Value: " + patient.getCommunication().get(l).getLanguage().getCoding().get(l).getDisplay());
                            languageValue = patient.getCommunication().get(l).getLanguage().getCoding().get(l).getDisplay().toLowerCase();
                            break;
                        } else {
                            System.out.println("smile language text Value: " + patient.getCommunication().get(l).getLanguage().getText());
                            languageValue = patient.getCommunication().get(l).getLanguage().getText().toLowerCase();
                            break;
                        }


                    }
                    System.out.println("smileLanguageValue: " + languageValue);
                    Assert.assertEquals(azureEXtensionValueString, languageValue);
                }


            }
            else if (resource.equalsIgnoreCase("procedure")) {
                if (urlValue.equals("dataSource")) {
//                    ObjectMapper objectMapper1 = new ObjectMapper();
//                    JsonNode jsonNodeName = objectMapper1.readTree(smileResponse.asString());
                    IParser parser = FhirContext.forR4().newJsonParser();
                    procedure = parser.parseResource(Procedure.class, objectMapper.writeValueAsString(smileJsonNode));
                    for (int l = 0; l < procedure.getIdentifier().size(); l++) {
                        if (procedure.getIdentifier().get(l).getSystem().equalsIgnoreCase("data_source")) {
                            System.out.println("data source Value: " + procedure.getIdentifier().get(l).getValue());
                            dataSourceValue = procedure.getIdentifier().get(l).getValue().toLowerCase();
                            break;
                        }
                    }
                    System.out.println("dataSourceValue: " + dataSourceValue);
                    Assert.assertEquals(azureEXtensionValueString, dataSourceValue);
                }
                if (urlValue.equals("category")) {
//                    ObjectMapper objectMapper1 = new ObjectMapper();
//                    JsonNode jsonNodeName = objectMapper1.readTree(smileResponse.asString());
                    IParser parser = FhirContext.forR4().newJsonParser();
                    procedure = parser.parseResource(Procedure.class, objectMapper.writeValueAsString(smileJsonNode));

                    //fetching data from code-able concept( "category.coding.display","category.text","category.coding.code")
                    for (int l = 0; l < procedure.getCategory().getCoding().size(); l++) {
//                        if (procedure.getCategory().getCoding().get(l).getDisplay()!=null) {
                            for (l = 0; l < procedure.getCategory().getCoding().size(); l++) {
                                if (procedure.getCategory().getCoding().get(l).getDisplay() != null) {
                                    categoryValue = procedure.getCategory().getCoding().get(l).getDisplay().toLowerCase();
                                    break;
                                }
                            }
//                            break;
//                        }
                        if (procedure.getCategory().getCoding().get(l).getDisplay()==null&&procedure.getCategory().getText()!=null) {
                            categoryValue =procedure.getCategory().getText();
                            break;
                        }
                        if (procedure.getCategory().getCoding().get(l).getDisplay()==null && procedure.getCategory().getText()==null) {
                            for (l = 0; l < procedure.getCategory().getCoding().size(); l++) {
                                if (procedure.getCategory().getCoding().get(l).getCode() != null) {
                                    categoryValue = procedure.getCategory().getCoding().get(l).getCode().toLowerCase();
                                    break;
                                }
                            }
                            break;
                        }


                    }
                    System.out.println("categoryValue: " + categoryValue);
                    categoryValue = categoryValue.replaceAll("[^a-zA-Z0-9:]", "").toLowerCase();
                    Assert.assertEquals(azureEXtensionValueString, categoryValue);
                }


                if (urlValue.equals("procedureName")) {
//                    ObjectMapper objectMapper1 = new ObjectMapper();
//                    JsonNode jsonNodeName = objectMapper1.readTree(smileResponse.asString());
                    IParser parser = FhirContext.forR4().newJsonParser();
                        procedure = parser.parseResource(Procedure.class, objectMapper.writeValueAsString(smileJsonNode));
                    for (int l = 0; l < procedure.getCode().getCoding().size(); l++) {
                        if (procedure.getCode().getCoding().get(l).getDisplay()!=null) {
                            System.out.println("procedure name Value: " + procedure.getCode().getCoding().get(l).getDisplay());
                            procedureNameValue = procedure.getCode().getCoding().get(l).getDisplay().toLowerCase();
                            break;
                        } else if (procedure.getCode().getText()!=null) {
                            procedureNameValue = procedure.getCode().getText().toLowerCase();
                            break;
                        }
                    }
                    System.out.println("procedureNameValue: " + procedureNameValue);
                    Assert.assertEquals(azureEXtensionValueString, procedureNameValue);
                }




            }
        }
    }


}
