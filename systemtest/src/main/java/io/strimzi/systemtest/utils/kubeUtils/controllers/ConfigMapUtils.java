/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest.utils.kubeUtils.controllers;

import io.fabric8.kubernetes.api.model.ConfigMap;

import io.strimzi.operator.common.model.Labels;
import io.strimzi.systemtest.Constants;
import io.strimzi.test.TestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.strimzi.systemtest.resources.ResourceManager.cmdKubeClient;
import static io.strimzi.test.k8s.KubeClusterResource.kubeClient;

public class ConfigMapUtils {

    private static final Logger LOGGER = LogManager.getLogger(ConfigMapUtils.class);

    private ConfigMapUtils() { }

    /**
     * Wait until the config map has been recovered.
     * @param name The name of the ConfigMap.
     */
    public static void waitForConfigMapRecovery(String name, String configMapUid) {
        LOGGER.info("Waiting for config map {}-{} recovery in namespace {}", name, configMapUid, kubeClient().getNamespace());
        TestUtils.waitFor("Config map " + name + " to be recovered", Constants.POLL_INTERVAL_FOR_RESOURCE_READINESS, Constants.TIMEOUT_FOR_RESOURCE_READINESS,
            () -> !kubeClient().getConfigMapUid(name).equals(configMapUid));
        LOGGER.info("Config map {} was recovered", name);
    }

    public static void waitForKafkaConfigMapLabelsChange(String configMapName, Map<String, String> labels) {
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            boolean isK8sTag = entry.getKey().equals("controller-revision-hash") || entry.getKey().equals("statefulset.kubernetes.io/pod-name");
            boolean isStrimziTag = entry.getKey().startsWith(Labels.STRIMZI_DOMAIN);
            // ignoring strimzi.io and k8s labels
            if (!(isStrimziTag || isK8sTag)) {
                LOGGER.info("Waiting for Kafka ConfigMap label change {} -> {}", entry.getKey(), entry.getValue());
                TestUtils.waitFor("Kafka ConfigMap label change " + entry.getKey() + " -> " + entry.getValue(), Constants.POLL_INTERVAL_FOR_RESOURCE_READINESS,
                    Constants.TIMEOUT_FOR_RESOURCE_READINESS, () ->
                        kubeClient().getConfigMap(configMapName).getMetadata().getLabels().get(entry.getKey()).equals(entry.getValue())
                );
            }
        }
    }

    public static void waitForKafkaConfigMapLabelsDeletion(String configMapName, String... labelKeys) {
        for (final String labelKey : labelKeys) {
            LOGGER.info("Waiting for Kafka ConfigMap label {} change to {}", labelKey, null);
            TestUtils.waitFor("Kafka configMap label" + labelKey + " change to " + null, Constants.POLL_INTERVAL_FOR_RESOURCE_READINESS,
                Constants.TIMEOUT_FOR_RESOURCE_READINESS, () ->
                    kubeClient().getConfigMap(configMapName).getMetadata().getLabels().get(labelKey) == null
            );
            LOGGER.info("Kafka ConfigMap label {} change to {}", labelKey, null);
        }
    }

    public static void waitUntilConfigMapDeletion(String clusterName) {
        LOGGER.info("Waiting for all ConfigMaps of cluster {} deletion", clusterName);
        TestUtils.waitFor("ConfigMaps will be deleted {}", Constants.GLOBAL_POLL_INTERVAL, Constants.GLOBAL_STATUS_TIMEOUT,
            () -> {
                List<ConfigMap> cmList = kubeClient().listConfigMaps().stream().filter(cm -> cm.getMetadata().getName().contains(clusterName)).collect(Collectors.toList());
                if (cmList.isEmpty()) {
                    return true;
                } else {
                    for (ConfigMap cm : cmList) {
                        LOGGER.warn("ConfigMap {} is not deleted yet! Triggering force delete by cmd client!", cm.getMetadata().getName());
                        cmdKubeClient().deleteByName("configmap", cm.getMetadata().getName());
                    }
                    return false;
                }
            });
        LOGGER.info("ConfigMaps of cluster {} were deleted", clusterName);
    }
}
