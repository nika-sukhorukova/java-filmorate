package ru.yandex.practicum.filmorate.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.filmorate.service.FilmService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FilmController.class)
class FilmControllerCommonFilmsWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FilmService filmService;

    @Test
    void common_withoutUserId_returns400() throws Exception {
        mockMvc.perform(get("/films/common")
                        .param("friendId", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ошибка валидации"))
                .andExpect(jsonPath("$.description").value(
                        "Не передан обязательный параметр: userId"));
    }

    @Test
    void common_withoutFriendId_returns400() throws Exception {
        mockMvc.perform(get("/films/common")
                        .param("userId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ошибка валидации"))
                .andExpect(jsonPath("$.description").value(
                        "Не передан обязательный параметр: friendId"));
    }

    @Test
    void common_withoutBothParams_returns400() throws Exception {
        mockMvc.perform(get("/films/common"))
                .andExpect(status().isBadRequest());
    }
}