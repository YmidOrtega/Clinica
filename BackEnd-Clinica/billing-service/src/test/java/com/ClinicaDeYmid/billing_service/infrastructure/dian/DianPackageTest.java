package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.application.dian.DianPackage;
import com.ClinicaDeYmid.billing_service.domain.Nit;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DianPackageTest {

    @Test
    void namesTheFilesWithTheNitTheYearAndAHexadecimalConsecutive() {
        assertThat(DianPackage.baseName(new Nit("800197268", 4), 2026, 26)).isEqualTo("080019726800026" + "0000001a");
        assertThatThrownBy(() -> DianPackage.baseName(new Nit("800197268", 4), 2026, 0x1_0000_0000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zipsTheSignedInvoiceUnderItsXmlName() throws Exception {
        DianPackage pack = DianPackage.of("0800197268000260000001a", "<Invoice>á</Invoice>");

        assertThat(pack.zipName()).isEqualTo("z0800197268000260000001a.zip");
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(pack.zip()), StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getNextEntry();
            assertThat(entry.getName()).isEqualTo("fv0800197268000260000001a.xml");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("<Invoice>á</Invoice>");
            assertThat(zip.getNextEntry()).isNull();
        }
    }
}
