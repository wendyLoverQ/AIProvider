package com.aiprovider.service;

import com.aiprovider.mapper.ComfyPresetMapper;
import com.aiprovider.model.dto.ComfyPresetDTO;
import com.aiprovider.model.vo.ComfyPresetVO;
import com.aiprovider.repository.ComfyPresetRepository;
import com.aiprovider.repository.PromptCatalogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ComfyPresetServiceTest {
    private static final Map<String, Boolean> CATEGORIES = new LinkedHashMap<>();
    static { CATEGORIES.put("Character", true); CATEGORIES.put("Expression", false); CATEGORIES.put("Quality", true); }
    private final ComfyPresetRepository presets = mock(ComfyPresetRepository.class);
    private final PromptCatalogRepository catalog = mock(PromptCatalogRepository.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ComfyPresetService service = new ComfyPresetService(presets, catalog, json);

    @BeforeEach void configureCatalog() { when(catalog.findEnabledOptions()).thenReturn(optionRows()); }

    private List<Map<String, Object>> optionRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : CATEGORIES.entrySet()) {
            Map<String, Object> row = new HashMap<>(); row.put("id", entry.getKey() + "-option"); row.put("category", entry.getKey()); row.put("allowMultiple", entry.getValue()); rows.add(row);
        }
        return rows;
    }

    private ComfyPresetDTO valid() {
        ComfyPresetDTO dto = new ComfyPresetDTO(); dto.setName(" Portrait ");
        Map<String, List<String>> selected = new LinkedHashMap<>();
        for (String key : CATEGORIES.keySet()) selected.put(key, new ArrayList<>());
        selected.get("Character").add("Character-option");
        dto.setSelectedOptions(selected); dto.setPositiveExtra(" extra "); dto.setNegativeExtra(" negative extra ");
        dto.setPositivePrompt(" final positive "); dto.setNegativePrompt(" final negative "); dto.setRemark(" note "); dto.setIsDefault(false);
        return dto;
    }

    @Test void createsStructuredSchemesAndHandlesDefaultState() {
        when(presets.insert(any())).thenAnswer(invocation -> { ComfyPresetMapper.PresetRecord value = invocation.getArgument(0); value.setId(9L); return 9L; });
        assertThat(service.create(valid())).isEqualTo(9L);
        verify(presets).insert(argThat(value -> value.getName().equals("Portrait") && value.getSelectedOptionsJson().contains("Character-option") &&
                value.getPositiveExtra().equals("extra") && value.getNegativeExtra().equals("negative extra") && value.getPositivePrompt().equals("final positive") &&
                value.getNegativePrompt().equals("final negative") && value.getRemark().equals("note") && !value.isDefault()));
        ComfyPresetDTO defaultDto = valid(); defaultDto.setIsDefault(true); defaultDto.setRemark("  "); service.create(defaultDto);
        verify(presets).clearDefault(); verify(presets).insert(argThat(value -> value.isDefault() && value.getRemark() == null));
        ComfyPresetDTO nullRemark = valid(); nullRemark.setRemark(null); service.create(nullRemark);
        verify(presets).insert(argThat(value -> !value.isDefault() && value.getRemark() == null));
    }

    @Test void listsEveryStoredFieldAndRejectsCorruptJson() {
        Map<String, Object> first = row(7L, true); first.put("positiveExtra", null); first.put("negativeExtra", "n");
        Map<String, Object> second = row("8", 1); second.put("name", null);
        Map<String, Object> third = row(9, "true");
        Map<String, Object> fourth = row(10, 0);
        Map<String, Object> fifth = row(11, "false");
        when(presets.findAll()).thenReturn(Arrays.asList(first, second, third, fourth, fifth));
        List<ComfyPresetVO> result = service.list();
        assertThat(result).extracting(ComfyPresetVO::getId).containsExactly(7L, 8L, 9L, 10L, 11L);
        assertThat(result).extracting(ComfyPresetVO::getIsDefault).containsExactly(true, true, true, false, false);
        assertThat(result.get(0).getPositiveExtra()).isEmpty(); assertThat(result.get(0).getNegativeExtra()).isEqualTo("n");
        assertThat(result.get(1).getName()).isNull(); assertThat(result.get(0).getSelectedOptions()).containsKey("Quality");
        first.put("selectedOptionsJson", "bad"); when(presets.findAll()).thenReturn(Collections.singletonList(first));
        assertThatThrownBy(service::list).isInstanceOf(IllegalStateException.class).hasMessageContaining("结构化选择 JSON");
    }

    private Map<String, Object> row(Object id, Object isDefault) {
        Map<String, Object> row = new HashMap<>(); row.put("id", id); row.put("name", "A"); row.put("selectedOptionsJson", selectionsJson());
        row.put("positiveExtra", "p"); row.put("negativeExtra", null); row.put("positivePrompt", "positive"); row.put("negativePrompt", null);
        row.put("remark", "remark"); row.put("isDefault", isDefault); return row;
    }
    private String selectionsJson() { try { return json.writeValueAsString(valid().getSelectedOptions()); } catch (JsonProcessingException e) { throw new AssertionError(e); } }

    @Test void updatesDefaultsDeletesAndRejectsMissingRows() {
        when(presets.update(any())).thenReturn(true); service.update(3, valid());
        verify(presets).update(argThat(value -> value.getId() == 3 && value.getName().equals("Portrait")));
        ComfyPresetDTO asDefault = valid(); asDefault.setIsDefault(true); service.update(3, asDefault); verify(presets, atLeast(1)).clearDefault();
        when(presets.update(any())).thenReturn(false);
        assertThatThrownBy(() -> service.update(4, valid())).isInstanceOf(IllegalArgumentException.class).hasMessage("Prompt 方案不存在");
        when(presets.setDefault(3)).thenReturn(true); service.setDefault(3); verify(presets).setDefault(3);
        when(presets.setDefault(4)).thenReturn(false);
        assertThatThrownBy(() -> service.setDefault(4)).isInstanceOf(IllegalArgumentException.class).hasMessage("Prompt 方案不存在");
        when(presets.delete(3)).thenReturn(true); service.delete(3);
        when(presets.delete(4)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(4)).isInstanceOf(IllegalArgumentException.class).hasMessage("Prompt 方案不存在");
    }

    @Test void validatesSchemeShapeSelectionsAndLengths() {
        assertInvalid(null, "不能为空");
        ComfyPresetDTO dto = valid(); dto.setName(null); assertInvalid(dto, "名称长度");
        dto = valid(); dto.setName(" "); assertInvalid(dto, "名称长度");
        dto = valid(); dto.setName(repeat(101)); assertInvalid(dto, "名称长度");
        dto = valid(); dto.setRemark(repeat(1001)); assertInvalid(dto, "备注");
        dto = valid(); dto.setSelectedOptions(null); assertInvalid(dto, "当前 Prompt 词条分类");
        dto = valid(); dto.getSelectedOptions().remove("Quality"); assertInvalid(dto, "当前 Prompt 词条分类");
        dto = valid(); dto.getSelectedOptions().put("legacy", new ArrayList<>()); assertInvalid(dto, "当前 Prompt 词条分类");
        dto = valid(); dto.getSelectedOptions().put("Quality", null); assertInvalid(dto, "不能为 null");
        dto = valid(); dto.getSelectedOptions().get("Expression").addAll(Arrays.asList("Expression-option", "expression-option-2")); assertInvalid(dto, "单选分类");
        dto = valid(); dto.getSelectedOptions().get("Quality").add(null); assertInvalid(dto, "空值或重复项");
        dto = valid(); dto.getSelectedOptions().get("Quality").add(" "); assertInvalid(dto, "空值或重复项");
        dto = valid(); dto.getSelectedOptions().get("Quality").addAll(Arrays.asList("Quality-option", "Quality-option")); assertInvalid(dto, "空值或重复项");
        dto = valid(); dto.getSelectedOptions().get("Quality").add("missing"); assertInvalid(dto, "不存在、未启用或分类不匹配");
        dto = valid(); dto.getSelectedOptions().get("Quality").add("Expression-option"); assertInvalid(dto, "分类不匹配");
        assertPromptInvalid("positiveExtra", null, "正向手动补充"); assertPromptInvalid("positiveExtra", repeat(8001), "正向手动补充");
        assertPromptInvalid("negativeExtra", null, "反向手动补充"); assertPromptInvalid("negativeExtra", repeat(8001), "反向手动补充");
        assertPromptInvalid("positivePrompt", null, "最终正向"); assertPromptInvalid("positivePrompt", repeat(16001), "最终正向");
        assertPromptInvalid("negativePrompt", null, "最终反向"); assertPromptInvalid("negativePrompt", repeat(16001), "最终反向");
    }

    @Test void reportsJsonSerializationFailureWithoutFallback() throws Exception {
        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new JsonProcessingException("broken") { });
        ComfyPresetService brokenService = new ComfyPresetService(presets, catalog, broken);
        assertThatThrownBy(() -> brokenService.create(valid())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("有效 JSON");
    }

    private void assertPromptInvalid(String field, String value, String message) {
        ComfyPresetDTO dto = valid();
        if (field.equals("positiveExtra")) dto.setPositiveExtra(value);
        if (field.equals("negativeExtra")) dto.setNegativeExtra(value);
        if (field.equals("positivePrompt")) dto.setPositivePrompt(value);
        if (field.equals("negativePrompt")) dto.setNegativePrompt(value);
        assertInvalid(dto, message);
    }
    private String repeat(int count) { return String.join("", Collections.nCopies(count, "x")); }
    private void assertInvalid(ComfyPresetDTO dto, String message) { assertThatThrownBy(() -> service.create(dto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message); }
}
