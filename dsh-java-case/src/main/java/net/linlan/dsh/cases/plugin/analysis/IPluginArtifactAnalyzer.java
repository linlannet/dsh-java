package net.linlan.dsh.cases.plugin.analysis;

import net.linlan.dsh.api.dto.PluginArtifactCandidateDTO;
import java.nio.file.Path;
import java.util.List;

public interface IPluginArtifactAnalyzer {

    PluginArtifactCandidateDTO analyzeJar(Path jarPath);

    List<PluginArtifactCandidateDTO> analyzeMavenDependency(String pomXml);
}
