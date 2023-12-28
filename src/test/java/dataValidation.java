import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.json.simple.JSONObject;
import org.junit.Assert;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.testng.asserts.SoftAssert;

public class dataValidation extends utils{
    static SoftAssert softAssert = new SoftAssert();
    static String ruleFile;
    static String fullAddress = "";
    static String email = "";
    static String phone = "";
    static String preferredName = "";
    static String previousName = "";
    static String ssnValue = "";
    static String dataSourceValue = "";
    static String categoryValue = "";
    static String procedureNameValue = "";

    static  Patient patient;
    static Procedure procedure;
    public static void verifyElement(int i, Response azureResponse, Response smileResponse) {
        ArrayList<JSONObject> smileData = getResourceValues("smileOutput");
        JSONObject jsonObj = smileData.get(i);
        Set<String> keys = jsonObj.keySet();
        String nodeElement = null;
        for (int j = i; j == i; j++) {
            for (String item : keys) {
                if (item.equalsIgnoreCase("meta") || item.equalsIgnoreCase("extension") || item.equalsIgnoreCase("id")) {
                    continue;
                }
                JsonPath j1 = azureResponse.jsonPath();
                System.out.println("-----------------------------------------------");
                System.out.println("component.........." + item);
                System.out.println("Azure " + j1.get(item).toString());
                String azureNodeElement = j1.get(item).toString();

                JsonPath j2 = smileResponse.jsonPath();
                nodeElement = j2.get(item).toString();
                System.out.println("Smile: " + nodeElement);
                softAssert.assertEquals(nodeElement, azureNodeElement);
            }
            softAssert.assertAll();
        }

    }


    public static void verifyExtensions(int i, Response azureResponse) throws Exception {
        ruleFile= getGlobalValue("inputFiles")+"//"+getGlobalValue("resource")+"//"+"rules.txt";
        ArrayList<String> extensions = getValueFromFile(ruleFile);
        System.out.println("From rules file "+extensions);
        List<String> expected = getValueFromCSV("expectedValues");
        System.out.println("From csv file "+expected.get(i));

           for(String value: extensions){
               if(expected.get(i).contains(value)){
                   readExtensionAndCompare(i,azureResponse,value);
           }
           else{
                   readExtensionAndCompare(i,azureResponse,value,getGlobalValue("resource"));
           }

           }
    }

    public static void readExtensionAndCompare(int i, Response azureResponse, String urlValue) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode azureJsonNode = objectMapper.readTree(azureResponse.asString());
        JsonNode urlExtension = null;
        String extensionValueString = null;

        for (JsonNode extension : azureJsonNode.path("extension")) {
            urlExtension = extension;
            extensionValueString = urlExtension.path("valueString").asText().replaceAll("[^a-zA-Z0-9:@.]", "").toLowerCase();
            if (urlValue.equalsIgnoreCase(extension.path("url").asText())) {
                List<String> expected = getValueFromCSV("expectedValues");
                String[] keyValuePairs = expected.get(i).split(",");

                // Iterate through each key-value pair
                for (String pair : keyValuePairs) {
                    // Split each pair by colon
                    String[] parts = pair.split(":");

                    // Check if the pair has both key and value
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();

                        if (extension.path("url").asText().equalsIgnoreCase(key)) {
                            if (key.equalsIgnoreCase(urlValue)) {
                                System.out.println("-----------------------------------------------");
                                System.out.println("component=" + extension.path("url").asText());
                                System.out.println(extensionValueString);
                                System.out.println(value.toLowerCase());
                                Assert.assertEquals(extensionValueString,value.toLowerCase().replaceAll("[^a-zA-Z0-9:@.]", "").replaceAll(" ",""));
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    public static void readExtensionAndCompare(int i, Response azureResponse, String urlValue,String resourceName) throws Exception {
        for (int j = i; j == i; j++) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode azureJsonNode = objectMapper.readTree(azureResponse.asString());
            ObjectMapper objectMapper1 = new ObjectMapper();
            JsonNode smileJsonNode = objectMapper1.readTree(smileResponse.asString());
            String azureExtensionValueString = null;

            // Find the object with the "url" equal to "urlValue"
            JsonNode urlExtension = null;
            for (JsonNode extension : azureJsonNode.path("extension")) {
                if (urlValue.equals(extension.path("url").asText())) {
                    urlExtension = extension;
                    break;
                }
            }

            // Get the valueString from the "urlValue" extension
            String extensionValueString = null;
            if (urlExtension != null) {
                extensionValueString = urlExtension.path("valueString").asText();
                System.out.println("**********************************************");
                System.out.println("Value for extension with URL " + urlValue + ": " + extensionValueString);
                azureExtensionValueString = extensionValueString.replaceAll("[^a-zA-Z0-9:@.]", "").toLowerCase();
                System.out.println("azureExtensionValueString: " + azureExtensionValueString);
            }
            else{
                System.out.println("****** No extension with "+ urlValue+"********");
            }
            

            if(resourceName.equalsIgnoreCase("patient")){
                if (urlValue.equalsIgnoreCase("name")) {
                    String smileName = null;
                    IParser parser = FhirContext.forR4().newJsonParser();
                    Patient patient = parser.parseResource(Patient.class, objectMapper.writeValueAsString(smileJsonNode));

                    JsonNode nameNode = azureJsonNode.path("name");
                    if (nameNode.isArray() && !nameNode.isEmpty()) {
                        String givenName = patient.getName().get(0).getGiven().toString().replaceAll("[^a-zA-Z0-9,|:\\s]", "");
                        String familyName = nameNode.get(0).path("family").asText();
                        String fullName = givenName + " " + familyName;
                        smileName = fullName.replace("|", " ").trim().toLowerCase().replaceAll(" ","");
                        System.out.println("smileName: " + smileName);
                    }

                    Assert.assertEquals(azureExtensionValueString, smileName);
                }
                if (urlValue.equals("address")) {
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
                    Assert.assertEquals(azureExtensionValueString, fullAddress);
                }


                if (urlValue.equals("email")) {
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
                    Assert.assertEquals(azureExtensionValueString, email);
                }


                if (urlValue.equals("phone")) {
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
                    Assert.assertEquals(azureExtensionValueString, phone);
                }


                if (urlValue.equals("preferredName")) {
                    IParser parser = FhirContext.forR4().newJsonParser();
                    Patient patient = parser.parseResource(Patient.class, objectMapper.writeValueAsString(smileJsonNode));
                    for (int l = 0; l < patient.getName().size(); l++) {
                        if (patient.getName().get(l).getUse().toString().equalsIgnoreCase("usual")) {
                            System.out.println("smile preferredName Value: " + patient.getName().get(l).getText());
                            preferredName = patient.getName().get(l).getText().toLowerCase().replaceAll(" ","");

                        }

                    }
                    System.out.println("smilePreferredNameValue: " + preferredName);
                    Assert.assertEquals(azureExtensionValueString, preferredName);
                }


                if (urlValue.equals("previousName")) {
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
                    Assert.assertEquals(azureExtensionValueString, previousName);
                }

                if (urlValue.equals("socialSecurityNumber")) {
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
                    Assert.assertEquals(azureExtensionValueString, ssnValue);
                }
            } else if (resourceName.equalsIgnoreCase("procedure")) {
                    if (urlValue.equalsIgnoreCase("dataSource")) {
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
                        Assert.assertEquals(azureExtensionValueString, dataSourceValue);

                    }


            }


        }

    }



}
