<script setup>
import { reactive, ref } from "vue";
import { useAuthStore } from "@/stores/auth";

const authStore = useAuthStore();
const activeTab = ref("login");

const loginForm = reactive({
  username: "",
  password: ""
});

const registerForm = reactive({
  username: "",
  email: "",
  password: ""
});

async function submitLogin() {
  await authStore.login(loginForm);
}

async function submitRegister() {
  await authStore.register(registerForm);
}
</script>

<template>
  <section class="glass-panel p-6 sm:p-8">
    <div class="mb-6">
      <p class="section-title">玩家登入</p>
      <h2 class="mt-3 text-3xl font-semibold text-white">登入或註冊後開始交易</h2>
      <p class="mt-2 text-sm text-slate-400">
        登入後會從後端載入帳戶、委託紀錄與角色權限，並由 Pinia 保存目前狀態。
      </p>
    </div>

    <div class="mb-6 grid grid-cols-2 gap-2 rounded-2xl border border-white/10 bg-white/5 p-1">
      <button
        class="rounded-xl px-4 py-2 text-sm transition"
        :class="activeTab === 'login' ? 'bg-emerald-400 text-slate-950' : 'text-slate-300'"
        @click="activeTab = 'login'"
      >
        登入
      </button>
      <button
        class="rounded-xl px-4 py-2 text-sm transition"
        :class="activeTab === 'register' ? 'bg-sky-400 text-slate-950' : 'text-slate-300'"
        @click="activeTab = 'register'"
      >
        註冊
      </button>
    </div>

    <form v-if="activeTab === 'login'" class="space-y-4" @submit.prevent="submitLogin">
      <label class="block space-y-2">
        <span class="text-sm text-slate-300">帳號</span>
        <input
          v-model="loginForm.username"
          class="w-full rounded-2xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm outline-none transition focus:border-emerald-400/60"
          type="text"
          required
        />
      </label>

      <label class="block space-y-2">
        <span class="text-sm text-slate-300">密碼</span>
        <input
          v-model="loginForm.password"
          class="w-full rounded-2xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm outline-none transition focus:border-emerald-400/60"
          type="password"
          required
        />
      </label>

      <button
        class="w-full rounded-2xl bg-emerald-400 px-4 py-3 text-sm font-semibold text-slate-950 transition hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="authStore.status === 'loading'"
      >
        {{ authStore.status === "loading" ? "登入中..." : "登入" }}
      </button>
    </form>

    <form v-else class="space-y-4" @submit.prevent="submitRegister">
      <label class="block space-y-2">
        <span class="text-sm text-slate-300">帳號</span>
        <input
          v-model="registerForm.username"
          class="w-full rounded-2xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm outline-none transition focus:border-sky-400/60"
          type="text"
          required
        />
      </label>

      <label class="block space-y-2">
        <span class="text-sm text-slate-300">Email</span>
        <input
          v-model="registerForm.email"
          class="w-full rounded-2xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm outline-none transition focus:border-sky-400/60"
          type="email"
          required
        />
      </label>

      <label class="block space-y-2">
        <span class="text-sm text-slate-300">密碼</span>
        <input
          v-model="registerForm.password"
          class="w-full rounded-2xl border border-white/10 bg-slate-950/60 px-4 py-3 text-sm outline-none transition focus:border-sky-400/60"
          type="password"
          minlength="6"
          required
        />
      </label>

      <button
        class="w-full rounded-2xl bg-sky-400 px-4 py-3 text-sm font-semibold text-slate-950 transition hover:bg-sky-300 disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="authStore.status === 'loading'"
      >
        {{ authStore.status === "loading" ? "註冊中..." : "建立帳號" }}
      </button>
    </form>

    <p v-if="authStore.error" class="mt-4 text-sm text-rose-300">
      {{ authStore.error }}
    </p>
  </section>
</template>
