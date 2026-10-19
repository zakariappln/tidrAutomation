package com.ppln.automation.api.bdd.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ppln.automation.api.client.MultipartField;
import com.ppln.automation.core.bdd.PlaceholderResolver;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.HashMap;
import java.util.List;
import org.testng.annotations.Test;

@Epic("Framework")
@Feature("Multipart form-field fixtures")
public class MultipartFixturesTest {

    @Test(
            groups = {"unit"},
            description = "Fixture file is named after the request path")
    public void namesFixtureAfterPath() {
        assertThat(MultipartFixtures.fixtureName("/api/Document/Upload"))
                .isEqualTo("Document.Upload.json");
        assertThat(MultipartFixtures.fixtureName("/api/CompanyInformation/Add?x=1"))
                .isEqualTo("CompanyInformation.Add.json");
    }

    @Test(
            groups = {"unit"},
            description = "Scalars, arrays and @file parts become form parts; placeholders resolve")
    public void loadsFieldsAndFiles() {
        var vars = new HashMap<String, String>();
        List<MultipartField> fields =
                MultipartFixtures.load("/api/QaSelfTest/Sample", new PlaceholderResolver(vars));

        assertThat(fields)
                .extracting(MultipartField::name)
                .containsExactly("Name", "IsActive", "DeletedId", "DeletedId", "Files");
        assertThat(fields.get(0).value()).isEqualTo("QA " + vars.get("uniqueString"));
        assertThat(fields.get(1).value()).isEqualTo("true");
        assertThat(fields.get(3).value()).isEqualTo("11111111-1111-1111-1111-111111111111");
        MultipartField file = fields.get(4);
        assertThat(file.isFile()).isTrue();
        assertThat(file.fileName()).isEqualTo("qa-selftest.txt");
        assertThat(file.mimeType()).isEqualTo("text/plain");
        assertThat(file.describe()).doesNotContain("self-test attachment");
    }

    @Test(
            groups = {"unit"},
            description = "A missing fixture names the file to create")
    public void missingFixtureNamesTheFile() {
        assertThatThrownBy(
                        () ->
                                MultipartFixtures.load(
                                        "/api/Nope/Missing",
                                        new PlaceholderResolver(new HashMap<>())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("testdata/multipart/Nope.Missing.json");
    }
}
