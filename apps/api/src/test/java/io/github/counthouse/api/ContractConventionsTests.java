package io.github.counthouse.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;

class ContractConventionsTests {

    @Test
    void everyOperationListsTheProblemAsItsDefaultResponse() {
        var contract = new OpenAPIV3Parser().read(ContractValidation.CONTRACT);

        assertThat(contract.getPaths().values())
                .flatMap(path -> path.readOperations())
                .allSatisfy(operation -> assertThat(operation.getResponses())
                        .as(operation.getOperationId())
                        .extractingByKey("default")
                        .extracting(ApiResponse::get$ref)
                        .isEqualTo("#/components/responses/Problem"));
    }
}
