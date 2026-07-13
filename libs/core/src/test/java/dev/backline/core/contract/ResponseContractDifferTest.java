package dev.backline.core.contract;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseContractDifferTest {

    @Test
    void addedPathIsAdditive() {
        CapturePair pair = pair(
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]},{\"path\":\"$.id\",\"types\":[\"number\"]}],\"truncated\":false}",
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]},{\"path\":\"$.id\",\"types\":[\"number\"]},{\"path\":\"$.name\",\"types\":[\"string\"]}],\"truncated\":false}");
        ContractChangeDetail detail = ResponseContractDiffer.diff(
                ResponseContractStatus.CAPTURED,
                pair.prevJson,
                pair.prevHash,
                ResponseContractStatus.CAPTURED,
                pair.curJson,
                pair.curHash);
        assertThat(detail.classification()).isEqualTo(ContractChangeClassification.ADDITIVE);
        assertThat(detail.changes()).hasSize(1);
        assertThat(detail.changes().getFirst().path()).isEqualTo("$.name");
    }

    @Test
    void removedPathIsBreaking() {
        CapturePair pair = pair(
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]},{\"path\":\"$.email\",\"types\":[\"string\"]}],\"truncated\":false}",
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]}],\"truncated\":false}");
        ContractChangeDetail detail = ResponseContractDiffer.diff(
                ResponseContractStatus.CAPTURED,
                pair.prevJson,
                pair.prevHash,
                ResponseContractStatus.CAPTURED,
                pair.curJson,
                pair.curHash);
        assertThat(detail.classification()).isEqualTo(ContractChangeClassification.BREAKING);
    }

    @Test
    void typeChangeIsBreaking() {
        CapturePair pair = pair(
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]},{\"path\":\"$.id\",\"types\":[\"number\"]}],\"truncated\":false}",
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]},{\"path\":\"$.id\",\"types\":[\"string\"]}],\"truncated\":false}");
        ContractChangeDetail detail = ResponseContractDiffer.diff(
                ResponseContractStatus.CAPTURED,
                pair.prevJson,
                pair.prevHash,
                ResponseContractStatus.CAPTURED,
                pair.curJson,
                pair.curHash);
        assertThat(detail.classification()).isEqualTo(ContractChangeClassification.BREAKING);
    }

    @Test
    void nullAdditionIsNoisy() {
        CapturePair pair = pair(
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]},{\"path\":\"$.email\",\"types\":[\"string\"]}],\"truncated\":false}",
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]},{\"path\":\"$.email\",\"types\":[\"null\",\"string\"]}],\"truncated\":false}");
        ContractChangeDetail detail = ResponseContractDiffer.diff(
                ResponseContractStatus.CAPTURED,
                pair.prevJson,
                pair.prevHash,
                ResponseContractStatus.CAPTURED,
                pair.curJson,
                pair.curHash);
        assertThat(detail.classification()).isEqualTo(ContractChangeClassification.NOISY);
    }

    @Test
    void sameHashIsUnchanged() {
        String json =
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]}],\"truncated\":false}";
        String hash = ResponseContractCanonicalizer.sha256Hex(json);
        ContractChangeDetail detail = ResponseContractDiffer.diff(
                ResponseContractStatus.CAPTURED,
                json,
                hash,
                ResponseContractStatus.CAPTURED,
                json,
                hash);
        assertThat(detail.classification()).isEqualTo(ContractChangeClassification.UNCHANGED);
        assertThat(detail.changes()).isEmpty();
    }

    @Test
    void missingBaselineIsUnavailable() {
        CapturePair cur = pair(
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]}],\"truncated\":false}",
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]}],\"truncated\":false}");
        ContractChangeDetail detail = ResponseContractDiffer.diff(
                null, null, null, ResponseContractStatus.CAPTURED, cur.curJson, cur.curHash);
        assertThat(detail.classification()).isEqualTo(ContractChangeClassification.UNAVAILABLE);
    }

    @Test
    void invalidJsonStatusIsUnavailable() {
        CapturePair cur = pair(
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]}],\"truncated\":false}",
                "{\"version\":1,\"root_type\":\"object\",\"paths\":[{\"path\":\"$\",\"types\":[\"object\"]}],\"truncated\":false}");
        ContractChangeDetail detail = ResponseContractDiffer.diff(
                ResponseContractStatus.INVALID_JSON,
                null,
                null,
                ResponseContractStatus.CAPTURED,
                cur.curJson,
                cur.curHash);
        assertThat(detail.classification()).isEqualTo(ContractChangeClassification.UNAVAILABLE);
    }

    @Test
    void nullOnlyAdditionHelper() {
        assertThat(ResponseContractDiffer.isNullOnlyAddition(List.of("string"), List.of("null", "string")))
                .isTrue();
        assertThat(ResponseContractDiffer.isNullOnlyAddition(List.of("string"), List.of("number", "string")))
                .isFalse();
    }

    private static CapturePair pair(String prevJson, String curJson) {
        return new CapturePair(
                prevJson,
                ResponseContractCanonicalizer.sha256Hex(prevJson),
                curJson,
                ResponseContractCanonicalizer.sha256Hex(curJson));
    }

    private record CapturePair(String prevJson, String prevHash, String curJson, String curHash) {}
}
