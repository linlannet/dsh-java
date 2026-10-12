package net.linlan.dsh.domain.storage.conversion.adapter.port;

public interface IOfficeConverterPort {

    String convertToPdf(String inputPath, String outputDir);
}
