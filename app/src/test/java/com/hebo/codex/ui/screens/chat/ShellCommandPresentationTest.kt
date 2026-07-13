package com.hebo.codex.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellCommandPresentationTest {
    @Test
    fun `隐藏 zsh 登录执行包装`() {
        assertEquals(
            "rg -n 'foo bar' .",
            shellCommandForDisplay("/usr/bin/zsh -lc \"rg -n 'foo bar' .\""),
        )
    }

    @Test
    fun `隐藏 bash 非登录执行包装并还原转义字符`() {
        assertEquals(
            "printf \"%s\\n\" value",
            shellCommandForDisplay("/bin/bash -c \"printf \\\"%s\\\\n\\\" value\""),
        )
    }

    @Test
    fun `保留包装内的多行脚本`() {
        val script = "printf first\nprintf second"

        assertEquals(
            script,
            shellCommandForDisplay("/usr/bin/zsh -lc '$script'"),
        )
    }

    @Test
    fun `支持相邻引号片段组成同一参数`() {
        assertEquals(
            "printf '%s' value",
            shellCommandForDisplay("/bin/sh -c 'printf '\"'\"'%s'\"'\"' value'"),
        )
    }

    @Test
    fun `未知 shell 保持原命令`() {
        val command = "/usr/bin/fish -lc 'pwd'"

        assertEquals(command, shellCommandForDisplay(command))
    }

    @Test
    fun `参数数量不符时保持原命令`() {
        val command = "/usr/bin/zsh -lc pwd extra"

        assertEquals(command, shellCommandForDisplay(command))
    }

    @Test
    fun `引号不完整时保持原命令`() {
        val command = "/usr/bin/zsh -lc 'pwd"

        assertEquals(command, shellCommandForDisplay(command))
    }
}
