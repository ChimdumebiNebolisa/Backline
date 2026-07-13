package dev.backline.executor.contract;

import dev.backline.core.contract.ContractLimits;
import dev.backline.core.contract.ContractPathEntry;
import dev.backline.core.contract.ContractSettingsDto;
import dev.backline.core.contract.ResponseContract;
import dev.backline.core.contract.ResponseContractCanonicalizer;
import dev.backline.core.contract.ResponseContractStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseContractCapturerTest {

    private final ResponseContractCapturer capturer = new ResponseContractCapturer();

    @Test
    void keyReorderAndValueChangeShareFingerprint() {
        var a = capturer.capture("{\"b\":1,\"a\":\"x\"}", "application/json", null, false);
        var b = capturer.capture("{\"a\":\"y\",\"b\":2}", "application/json", null, false);
        assertThat(a.status()).isEqualTo(ResponseContractStatus.CAPTURED);
        assertThat(a.fingerprint()).isEqualTo(b.fingerprint());
        assertThat(a.contractJson()).isEqualTo(b.contractJson());
    }

    @Test
    void emptyObjectAndEmptyArray() {
        var emptyObj = capturer.capture("{}", "application/json", null, false);
        assertThat(pathSet(emptyObj.contract())).containsExactly("$");
        assertThat(typesAt(emptyObj.contract(), "$")).containsExactly("object");

        var emptyArr = capturer.capture("[]", "application/json", null, false);
        assertThat(pathSet(emptyArr.contract())).containsExactly("$");
        assertThat(typesAt(emptyArr.contract(), "$")).containsExactly("array");
    }

    @Test
    void homogeneousAndHeterogeneousArrays() {
        var homo = capturer.capture("{\"items\":[{\"id\":1},{\"id\":2}]}", "application/json", null, false);
        assertThat(pathSet(homo.contract())).contains("$.items", "$.items[]", "$.items[].id");
        assertThat(typesAt(homo.contract(), "$.items[].id")).containsExactly("number");

        var hetero = capturer.capture("{\"items\":[1,\"x\",null]}", "application/json", null, false);
        assertThat(typesAt(hetero.contract(), "$.items[]")).containsExactly("null", "number", "string");
    }

    @Test
    void nestedStructuresAndNull() {
        var outcome = capturer.capture(
                "{\"user\":{\"email\":null,\"active\":true}}", "application/json", null, false);
        assertThat(typesAt(outcome.contract(), "$.user.email")).containsExactly("null");
        assertThat(typesAt(outcome.contract(), "$.user.active")).containsExactly("boolean");
    }

    @Test
    void ignorePathsRemovedBeforeFingerprint() {
        ContractSettingsDto settings = new ContractSettingsDto(true, "warn", List.of("$.meta.generated_at"));
        var withMeta = capturer.capture(
                "{\"id\":1,\"meta\":{\"generated_at\":\"t\"}}", "application/json", settings, false);
        var withoutMetaField = capturer.capture(
                "{\"id\":1,\"meta\":{}}", "application/json", settings, false);
        assertThat(withMeta.fingerprint()).isEqualTo(withoutMetaField.fingerprint());
        assertThat(pathSet(withMeta.contract())).doesNotContain("$.meta.generated_at");
        assertThat(pathSet(withMeta.contract())).contains("$.meta");
    }

    @Test
    void invalidJsonIsNotEmptyContract() {
        var outcome = capturer.capture("{not-json", "application/json", null, false);
        assertThat(outcome.status()).isEqualTo(ResponseContractStatus.INVALID_JSON);
        assertThat(outcome.contractJson()).isNull();
        assertThat(outcome.fingerprint()).isNull();
    }

    @Test
    void disabledSkipsCapture() {
        var outcome = capturer.capture("{\"a\":1}", "application/json", new ContractSettingsDto(false, "warn", null), false);
        assertThat(outcome.status()).isEqualTo(ResponseContractStatus.DISABLED);
    }

    @Test
    void notJsonMediaTypeWithoutJsonBody() {
        var outcome = capturer.capture("plain", "text/plain", null, false);
        assertThat(outcome.status()).isEqualTo(ResponseContractStatus.NOT_JSON);
    }

    @Test
    void depthCapMarksTruncated() {
        String json = IntStream.range(0, ContractLimits.MAX_JSON_DEPTH + 5)
                .mapToObj(i -> "{\"n\":")
                .collect(Collectors.joining())
                + "1"
                + "}".repeat(ContractLimits.MAX_JSON_DEPTH + 5);
        var outcome = capturer.capture(json, "application/json", null, false);
        assertThat(outcome.status()).isEqualTo(ResponseContractStatus.TRUNCATED);
        assertThat(outcome.contract().truncated()).isTrue();
        assertThat(outcome.contract().truncationReason()).isEqualTo(ContractLimits.REASON_DEPTH);
    }

    @Test
    void pathOrderingIsDeterministic() {
        var outcome = capturer.capture("{\"z\":1,\"a\":{\"c\":true,\"b\":2}}", "application/json", null, false);
        List<String> paths = outcome.contract().paths().stream().map(ContractPathEntry::path).toList();
        assertThat(paths).isSorted();
        String again = ResponseContractCanonicalizer.toCanonicalJson(outcome.contract());
        assertThat(again).isEqualTo(outcome.contractJson());
    }

    private static List<String> pathSet(ResponseContract contract) {
        return contract.paths().stream().map(ContractPathEntry::path).toList();
    }

    private static List<String> typesAt(ResponseContract contract, String path) {
        return contract.paths().stream()
                .filter(p -> p.path().equals(path))
                .findFirst()
                .orElseThrow()
                .types();
    }
}
