public enum APIResources {

    AddAPI("fhir/DEFAULT/{resourceName}"),
    GetAPI("fhir/DEFAULT/{resourceName}/{id}");
    private String resource;

    APIResources(String resource) {
        this.resource=resource;
    }

    public String getResource(){
        return resource;
    }
}
