package io.swagger.codegen.v3.generators.java;

import io.swagger.codegen.v3.CliOption;
import io.swagger.codegen.v3.CodegenConstants;
import io.swagger.codegen.v3.CodegenModel;
import io.swagger.codegen.v3.CodegenOperation;
import io.swagger.codegen.v3.CodegenProperty;
import io.swagger.codegen.v3.SupportingFile;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JavaJAXRSSpecServerCodegen extends AbstractJavaJAXRSServerCodegen {

    public static final String INTERFACE_ONLY = "interfaceOnly";
    public static final String GENERATE_POM = "generatePom";
    public static final String USE_TAGS = "useTags";
    public static final String RETURN_RESPONSE = "returnResponse";

    protected boolean useTags = false;
    protected boolean returnResponse = false;

    private boolean interfaceOnly = false;
    private boolean generatePom = true;

    public JavaJAXRSSpecServerCodegen() {
        super();
        invokerPackage = "io.swagger.api";
        artifactId = "swagger-jaxrs-server";
        outputFolder = "generated-code/JavaJaxRS-Spec";

        additionalProperties.put("title", title);

        typeMapping.put("date", "LocalDate");

        importMapping.put("LocalDate", "org.joda.time.LocalDate");

        for (int i = 0; i < cliOptions.size(); i++) {
            if (CodegenConstants.LIBRARY.equals(cliOptions.get(i).getOpt())) {
                cliOptions.remove(i);
                break;
            }
        }

        CliOption library = new CliOption(CodegenConstants.LIBRARY, "library template (sub-template) to use");
        library.setDefault(DEFAULT_LIBRARY);

        Map<String, String> supportedLibraries = new LinkedHashMap<String, String>();

        supportedLibraries.put(DEFAULT_LIBRARY, "JAXRS");
        library.setEnum(supportedLibraries);

        cliOptions.add(library);
        cliOptions.add(CliOption.newBoolean(GENERATE_POM, "Whether to generate pom.xml if the file does not already exist.").defaultValue(String.valueOf(generatePom)));
        cliOptions.add(CliOption.newBoolean(INTERFACE_ONLY, "Whether to generate only API interface stubs without the server files.").defaultValue(String.valueOf(interfaceOnly)));
        cliOptions.add(CliOption.newBoolean(USE_TAGS, "Whether to use tags for grouping Operations.").defaultValue(String.valueOf(useTags)));
        cliOptions.add(CliOption.newBoolean(RETURN_RESPONSE, "Whether to return a javax.ws.rs.core.Response.").defaultValue(String.valueOf(returnResponse)));
    }

    @Override
    public void processOpts() {
        if (additionalProperties.containsKey(GENERATE_POM)) {
            generatePom = Boolean.valueOf(additionalProperties.get(GENERATE_POM).toString());
        }
        if (additionalProperties.containsKey(INTERFACE_ONLY)) {
            interfaceOnly = Boolean.valueOf(additionalProperties.get(INTERFACE_ONLY).toString());
        }
        if (additionalProperties.containsKey(USE_TAGS)) {
            this.setUseTags(Boolean.valueOf(additionalProperties.get(USE_TAGS).toString()));
        }
        if (additionalProperties.containsKey(RETURN_RESPONSE)) {
            this.setReturnResponse(Boolean.valueOf(additionalProperties.get(RETURN_RESPONSE).toString()));
        }

        if (interfaceOnly) {
            // Change default artifactId if genereating interfaces only, before
            // command line options are applied in base class.
            artifactId = "swagger-jaxrs-client";
        }

        super.processOpts();

        modelTemplateFiles.put("model.mustache", ".java");
        apiTemplateFiles.put("api.mustache", ".java");

        if (StringUtils.isEmpty(apiPackage)) {
            apiPackage = "io.swagger.api";
        }
        if (StringUtils.isEmpty(modelPackage)) {
            modelPackage = "io.swagger.model";
        }

        apiTestTemplateFiles.clear(); // TODO: add api test template
        modelTestTemplateFiles.clear(); // TODO: add model test template

        // clear model and api doc template as this codegen
        // does not support auto-generated markdown doc at the moment
        // TODO: add doc templates
        modelDocTemplateFiles.remove("model_doc.mustache");
        apiDocTemplateFiles.remove("api_doc.mustache");

        supportingFiles.clear(); // Don't need extra files provided by
                                 // AbstractJAX-RS & Java Codegen
        if (generatePom) {
            writeOptional(outputFolder, new SupportingFile("pom.mustache", "", "pom.xml"));
        }
        if (!interfaceOnly) {
            writeOptional(outputFolder, new SupportingFile("RestApplication.mustache", (sourceFolder + '/' + invokerPackage).replace(".", "/"), "RestApplication.java"));
        }
    }

    @Override
    public String getDefaultTemplateDir() {
        return JAXRS_TEMPLATE_DIRECTORY_NAME + "/spec";
    }

    @Override
    public String getName() {
        return "jaxrs-spec";
    }

    /* (non-Javadoc)
     * @see io.swagger.codegen.languages.AbstractJavaJAXRSServerCodegen#postProcessOperations(java.util.Map)
     */
    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        objs = super.postProcessOperations(objs);

        if (useTags) {
            @SuppressWarnings("unchecked")
            Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
            if (operations != null) {

                // collect paths
                List<String> allPaths = new ArrayList<>();
                @SuppressWarnings("unchecked")
                List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
                for (CodegenOperation operation : ops) {
                    String path = operation.path;
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                    allPaths.add(path);
                }

                if (!allPaths.isEmpty()) {
                    // find common prefix
                    StringBuilder basePathSB = new StringBuilder();
                    String firstPath = allPaths.remove(0);
                    String[] parts = firstPath.split("/");
                    partsLoop:
                    for (String part : parts) {
                        for (String path : allPaths) {
                            if (!path.startsWith(basePathSB.toString() + part)) {
                                break partsLoop;
                            }
                        }
                        basePathSB.append(part).append("/");
                    }
                    String basePath = basePathSB.toString();
                    if (basePath.endsWith("/")) {
                        basePath = basePath.substring(0, basePath.length() - 1);
                    }

                    if (basePath.length() > 0) {
                        // update operations
                        for (CodegenOperation operation : ops) {
                            operation.path = operation.path.substring(basePath.length() + (operation.path.startsWith("/") ? 1 : 0));
                            operation.baseName = basePath;
                            operation.subresourceOperation = !operation.path.isEmpty();
                        }

                        // save base path in objects
                        objs.put("apiBasePath", basePath);
                    } else {
                        for (CodegenOperation operation : ops) {
                            operation.subresourceOperation = !operation.path.isEmpty();
                        }
                        objs.put("apiBasePath", "");
                        objs.put("baseName", "");
                    }
                }
            }
        }

        return objs;
    }

    @Override
    public void addOperationToGroup(String tag, String resourcePath, Operation operation, CodegenOperation co, Map<String, List<CodegenOperation>> operations) {
        if (useTags) {
            // only add operations to group; base path extraction is done in postProcessOperations
            List<CodegenOperation> opList = operations.get(tag);
            if (opList == null) {
                opList = new ArrayList<CodegenOperation>();
                operations.put(tag, opList);
            }
            opList.add(co);
        } else {
            String basePath = resourcePath;
            if (basePath.startsWith("/")) {
                basePath = basePath.substring(1);
            }
            int pos = basePath.indexOf("/");
            if (pos > 0) {
                basePath = basePath.substring(0, pos);
            }

            if (basePath == "") {
                basePath = "default";
            }
            else {
                if (co.path.startsWith("/" + basePath)) {
                    co.path = co.path.substring(("/" + basePath).length());
                }
                co.subresourceOperation = !co.path.isEmpty();
            }
            List<CodegenOperation> opList = operations.get(basePath);
            if (opList == null) {
                opList = new ArrayList<CodegenOperation>();
                operations.put(basePath, opList);
            }
            opList.add(co);
            co.baseName = basePath;
        }
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if (useOas2) {
            model.imports.remove("ApiModelProperty");
            model.imports.remove("ApiModel");
        } else {
            model.imports.remove("Schema");
        }
        model.imports.remove("JsonSerialize");
        model.imports.remove("ToStringSerializer");
        model.imports.remove("JsonValue");
        model.imports.remove("JsonProperty");
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
//        this.openAPIUtil = new OpenAPIUtil(openAPI);
        // copy input swagger to output folder
        try {
            String swaggerJson = Json.pretty(openAPI);
            FileUtils.writeStringToFile(new File(outputFolder + File.separator + "swagger.json"), swaggerJson);
        }
        catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e.getCause());
        }
        super.preprocessOpenAPI(openAPI);

    }

    @Override
    public String getHelp() {
        return "[WORK IN PROGRESS: generated code depends from Swagger v2 libraries] "
            + "Generates a Java JAXRS Server according to JAXRS 2.0 specification.";
    }

    public void setUseTags(boolean useTags) {
        this.useTags = useTags;
    }

    public void setReturnResponse(boolean returnResponse) {
        this.returnResponse = returnResponse;
    }
}
