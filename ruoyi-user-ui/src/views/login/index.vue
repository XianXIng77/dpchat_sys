<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import {
  NButton, NInput, NSpin, NText, useMessage,
  NIcon, useThemeVars, NCard, NSwitch
} from "naive-ui";
import { LoginFrom } from "@/typings/user";
import {
  PersonOutline,
  LockClosedOutline,
  LogInOutline,
  EyeOutline,
  EyeOffOutline,
  SparklesOutline,
  RocketOutline,
  ShieldCheckmarkOutline,
  MoonOutline,
  SunnyOutline,
  ColorPaletteOutline,
  MusicalNotesOutline
} from '@vicons/ionicons5';

import { useUserStore } from "@/store/modules/user";
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const userStore = useUserStore();
const router = useRouter();
const message = useMessage();
const themeVars = useThemeVars();

// 表单状态管理
const loginForm = reactive<LoginFrom>({
  username: '',
  password: '',
  type: ''
});

// 加载状态管理
const loginLoading = ref(false);
const pageLoading = ref(false);
const showPassword = ref(false);

// 动画状态
const isAnimating = ref(false);
const isDarkMode = ref(false);
const enableSound = ref(false);
const currentTheme = ref(1); // 梦幻紫主题

// 粒子系统
const particles = ref<Array<{id: number, x: number, y: number, size: number, speed: number, opacity: number, color: string}>>([]);
const mouseParticles = ref<Array<{id: number, x: number, y: number, vx: number, vy: number, life: number, size: number}>>([]);

// 鼠标位置
const mousePosition = ref({ x: 0, y: 0 });

// 表单验证
const formErrors = reactive({
  username: '',
  password: ''
});

// 主题配置
const themes = [
  { name: '极光', gradient: 'linear-gradient(135deg, #0f0f23 0%, #1a1a2e 50%, #16213e 100%)' },
  { name: '梦幻紫', gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 50%, #f093fb 100%)' },
  { name: '海洋蓝', gradient: 'linear-gradient(135deg, #2196F3 0%, #21CBF3 50%, #64B5F6 100%)' },
  { name: '日落橙', gradient: 'linear-gradient(135deg, #FF6B6B 0%, #FFE66D 50%, #FF8E53 100%)' },
  { name: '森林绿', gradient: 'linear-gradient(135deg, #4CAF50 0%, #8BC34A 50%, #CDDC39 100%)' },
  { name: '粉黛色', gradient: 'linear-gradient(135deg, #F9A8D4 0%, #F472B6 50%, #EC4899 100%)' },
  { name: '星空紫', gradient: 'linear-gradient(135deg, #4F46E5 0%, #7C3AED 50%, #8B5CF6 100%)' },
  { name: '晨曦黄', gradient: 'linear-gradient(135deg, #FFD700 0%, #FFB74D 50%, #FFA726 100%)' },
  { name: '极光绿', gradient: 'linear-gradient(135deg, #10B981 0%, #34D399 50%, #6EE7B7 100%)' },
  { name: '水墨青', gradient: 'linear-gradient(135deg, #06B6D4 0%, #22D3EE 50%, #67E8F9 100%)' }
];

// 创建粒子动画
const createParticles = () => {
  particles.value = [];
  const colors = ['#0f0f23', '#1a1a2e', '#16213e', '#667eea', '#764ba2', '#f093fb', '#2196F3', '#21CBF3'];
  
  for (let i = 0; i < 80; i++) {
    particles.value.push({
      id: i,
      x: Math.random() * window.innerWidth,
      y: Math.random() * window.innerHeight,
      size: Math.random() * 4 + 1,
      speed: Math.random() * 3 + 0.5,
      opacity: Math.random() * 0.7 + 0.3,
      color: colors[Math.floor(Math.random() * colors.length)]
    });
  }
};

// 创建鼠标跟随粒子
const createMouseParticle = (x: number, y: number) => {
  const id = Date.now() + Math.random();
  mouseParticles.value.push({
    id,
    x,
    y,
    vx: (Math.random() - 0.5) * 4,
    vy: (Math.random() - 0.5) * 4,
    life: 1,
    size: Math.random() * 3 + 2
  });
};

// 动画粒子
const animateParticles = () => {
  // 背景粒子
  particles.value.forEach(particle => {
    particle.y -= particle.speed;
    particle.x += Math.sin(particle.y * 0.01) * 0.5;
    
    if (particle.y < -10) {
      particle.y = window.innerHeight + 10;
      particle.x = Math.random() * window.innerWidth;
    }
    if (particle.x < -10 || particle.x > window.innerWidth + 10) {
      particle.x = Math.random() * window.innerWidth;
    }
  });

  // 鼠标跟随粒子
  mouseParticles.value = mouseParticles.value.filter(particle => {
    particle.x += particle.vx;
    particle.y += particle.vy;
    particle.life -= 0.02;
    particle.vx *= 0.98;
    particle.vy *= 0.98;
    return particle.life > 0;
  });
};

// 鼠标移动事件
const handleMouseMove = (e: MouseEvent) => {
  mousePosition.value = { x: e.clientX, y: e.clientY };
  
  // 创建鼠标跟随粒子
  if (Math.random() < 0.3) {
    createMouseParticle(e.clientX, e.clientY);
  }
};

// 鼠标点击事件
const handleMouseClick = (e: MouseEvent) => {
  // 创建点击爆炸效果
  for (let i = 0; i < 10; i++) {
    createMouseParticle(e.clientX, e.clientY);
  }
};

let animationId: number;

onMounted(() => {
  createParticles();
  const animate = () => {
    animateParticles();
    animationId = requestAnimationFrame(animate);
  };
  animate();

  // 添加鼠标事件监听
  document.addEventListener('mousemove', handleMouseMove);
  document.addEventListener('click', handleMouseClick);
});

onUnmounted(() => {
  if (animationId) {
    cancelAnimationFrame(animationId);
  }
  document.removeEventListener('mousemove', handleMouseMove);
  document.removeEventListener('click', handleMouseClick);
});

// 验证表单
function validateForm() {
  let isValid = true;

  // 用户名验证
  if (!loginForm.username) {
    formErrors.username = t('login.usernameRequired');
    isValid = false;
  } else {
    formErrors.username = '';
  }

  // 密码验证
  if (!loginForm.password) {
    formErrors.password = t('login.passwordRequired');
    isValid = false;
  } else {
    formErrors.password = '';
  }

  return isValid;
}

// 账号密码登录
async function handleLogin(e: MouseEvent) {
  if (!validateForm()) return;

  try {
    isAnimating.value = true;
    loginLoading.value = true;
    await userStore.userLogin(loginForm);
    message.success(t("login.loginSuccess"));
    // 添加成功动画延迟
    setTimeout(() => {
      router.push("/");
    }, 1000);
  } catch (error: any) {
    message.error(error.message || t("login.loginFailed"));
    isAnimating.value = false;
  } finally {
    loginLoading.value = false;
  }
}

// 跳转到注册页面
const navigateToRegister = () => {
  router.push("/regist");
};

// 切换密码显示
const togglePasswordVisibility = () => {
  showPassword.value = !showPassword.value;
};

// 切换主题
const switchTheme = () => {
  currentTheme.value = (currentTheme.value + 1) % themes.length;
  createParticles(); // 重新创建粒子以匹配新主题
};

// 切换暗黑模式
const toggleDarkMode = () => {
  isDarkMode.value = !isDarkMode.value;
  document.documentElement.classList.toggle('dark', isDarkMode.value);
};

// 计算背景样式
const brandSectionStyle = computed(() => {
  return {
    background: themes[currentTheme.value].gradient
  };
});

// 计算当前主题名称
const currentThemeName = computed(() => {
  return themes[currentTheme.value].name;
});

</script>

<template>
  <div class="login-container" :style="brandSectionStyle">
    <!-- 动态粒子背景 -->
    <div class="particles-background">
      <div 
        v-for="particle in particles" 
        :key="particle.id"
        class="particle"
        :style="{
          left: particle.x + 'px',
          top: particle.y + 'px',
          width: particle.size + 'px',
          height: particle.size + 'px',
          opacity: particle.opacity,
          backgroundColor: particle.color,
          boxShadow: `0 0 ${particle.size * 2}px ${particle.color}`
        }"
      ></div>
    </div>

    <!-- 鼠标跟随粒子 -->
    <div class="mouse-particles">
      <div 
        v-for="particle in mouseParticles" 
        :key="particle.id"
        class="mouse-particle"
        :style="{
          left: particle.x + 'px',
          top: particle.y + 'px',
          width: particle.size + 'px',
          height: particle.size + 'px',
          opacity: particle.life
        }"
      ></div>
    </div>

    <!-- 浮动几何图形 -->
    <div class="floating-shapes">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
      <div class="shape shape-3"></div>
      <div class="shape shape-4"></div>
      <div class="shape shape-5"></div>
    </div>

    <!-- 控制面板 -->
    <div class="control-panel">
      <div class="control-item" @click="switchTheme" title="切换主题">
        <NIcon :component="ColorPaletteOutline" size="20" />
        <span class="control-label">{{ currentThemeName }}</span>
      </div>
      <div class="control-item" @click="toggleDarkMode" title="切换暗黑模式">
        <NIcon :component="isDarkMode ? SunnyOutline : MoonOutline" size="20" />
      </div>
      <div class="control-item" @click="enableSound = !enableSound" title="音效开关">
        <NIcon :component="MusicalNotesOutline" size="20" :class="{ active: enableSound }" />
      </div>
    </div>

    <NSpin :show="pageLoading">
      <div class="login-content">
        <!-- 左侧品牌区域 -->
        <div class="brand-section">
          <div class="brand-content">
            <div class="logo-container">
              <div class="logo-icon">
                <NIcon :component="SparklesOutline" size="48" />
              </div>
              <div class="logo-glow"></div>
              <div class="logo-rings">
                <div class="ring ring-1"></div>
                <div class="ring ring-2"></div>
                <div class="ring ring-3"></div>
              </div>
            </div>
            <h1 class="brand-title">
              <span class="title-main">家庭用药</span>
              <span class="title-sub">科普平台</span>
            </h1>
            <p class="brand-description">
              让您的生活更健康<br>
              专业用药指导，守护家庭健康
            </p>
            <div class="feature-list">
              <div class="feature-item">
                <NIcon :component="RocketOutline" />
                <span>用药指导</span>
              </div>
              <div class="feature-item">
                <NIcon :component="ShieldCheckmarkOutline" />
                <span>安全用药</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧登录表单 -->
        <div class="form-wrapper">
          <NCard class="login-card" :bordered="false">
            <div class="card-header">
              <h2 class="welcome-title">欢迎回来</h2>
              <p class="welcome-subtitle">请登录您的账户</p>
            </div>

            <div class="form-content">
              <!-- 用户名输入 -->
              <div class="input-group">
                <div class="input-label-row">
                  <NText strong class="input-label">
                    {{ $t("login.username") }}
                  </NText>
                  <div v-if="formErrors.username" class="error-message">{{ formErrors.username }}</div>
                </div>
                <div class="input-wrapper">
                  <NInput 
                    v-model:value="loginForm.username" 
                    :placeholder="$t('login.enterEmailOrPhone')" 
                    round 
                    clearable
                    class="custom-input" 
                    :status="formErrors.username ? 'error' : undefined"
                    size="large"
                  >
                    <template #prefix>
                      <NIcon :component="PersonOutline" class="input-icon" />
                    </template>
                  </NInput>
                  <div class="input-glow"></div>
                </div>
              </div>

              <!-- 密码输入 -->
              <div class="input-group">
                <div class="input-label-row">
                  <NText strong class="input-label">
                    {{ $t("login.password") }}
                  </NText>
                  <div v-if="formErrors.password" class="error-message">{{ formErrors.password }}</div>
                </div>
                <div class="input-wrapper">
                  <NInput 
                    v-model:value="loginForm.password" 
                    :type="showPassword ? 'text' : 'password'" 
                    :placeholder="$t('login.enterPassword')" 
                    round
                    class="custom-input" 
                    :status="formErrors.password ? 'error' : undefined"
                    size="large"
                  >
                    <template #prefix>
                      <NIcon :component="LockClosedOutline" class="input-icon" />
                    </template>
                    <template #suffix>
                      <NIcon 
                        :component="showPassword ? EyeOffOutline : EyeOutline" 
                        class="password-toggle"
                        @click="togglePasswordVisibility"
                      />
                    </template>
                  </NInput>
                  <div class="input-glow"></div>
                </div>
              </div>

              <!-- 额外链接 -->
              <div class="additional-links">
                <NButton text tag="a" href="#/resetpassword" class="forgot-password">
                  {{ $t("login.forgotPassword") }}
                </NButton>
              </div>

              <!-- 登录按钮 -->
              <div class="action-buttons">
                <NButton 
                  type="primary" 
                  block 
                  :loading="loginLoading" 
                  @click="handleLogin" 
                  class="login-button"
                  size="large"
                  :class="{ 'success-animation': isAnimating }"
                >
                  <template #icon>
                    <NIcon :component="LogInOutline" />
                  </template>
                  {{ $t("login.login") }}
                  <div class="button-shine"></div>
                </NButton>
              </div>
              
              <!-- 注册提示 -->
              <div class="register-prompt">
                <span>还没有账号？</span>
                <NButton text type="primary" @click="navigateToRegister" class="register-link">
                  立即注册
                </NButton>
                <span>获取专业用药指导</span>
              </div>
            </div>
          </NCard>
        </div>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
/* 主容器 */
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 50%, #f093fb 100%);
  background-size: 400% 400%;
  animation: gradientShift 15s ease infinite;
}

@keyframes gradientShift {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

/* 粒子背景 */
.particles-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 2;
}

.particle {
  position: absolute;
  border-radius: 50%;
  animation: float 6s ease-in-out infinite;
  filter: blur(0.5px);
}

@keyframes float {
  0%, 100% { transform: translateY(0px) rotate(0deg) scale(1); }
  50% { transform: translateY(-20px) rotate(180deg) scale(1.2); }
}

/* 鼠标跟随粒子 */
.mouse-particles {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 3;
}

.mouse-particle {
  position: absolute;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.8) 0%, transparent 70%);
  border-radius: 50%;
  animation: mouseParticleFade 1s ease-out forwards;
}

@keyframes mouseParticleFade {
  0% { 
    transform: scale(0) rotate(0deg);
    opacity: 1;
  }
  100% { 
    transform: scale(1) rotate(360deg);
    opacity: 0;
  }
}

/* 浮动几何图形 */
.floating-shapes {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.shape {
  position: absolute;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  animation: floatShape 8s ease-in-out infinite;
}

.shape-1 {
  width: 80px;
  height: 80px;
  top: 20%;
  left: 10%;
  border-radius: 50%;
  animation-delay: 0s;
}

.shape-2 {
  width: 120px;
  height: 120px;
  top: 60%;
  right: 15%;
  border-radius: 20px;
  animation-delay: 2s;
}

.shape-3 {
  width: 60px;
  height: 60px;
  top: 80%;
  left: 20%;
  border-radius: 50%;
  animation-delay: 4s;
}

.shape-4 {
  width: 100px;
  height: 100px;
  top: 30%;
  right: 30%;
  border-radius: 20px;
  animation-delay: 6s;
}

.shape-5 {
  width: 40px;
  height: 40px;
  top: 10%;
  right: 10%;
  border-radius: 50%;
  animation-delay: 8s;
}

@keyframes floatShape {
  0%, 100% { transform: translateY(0px) rotate(0deg) scale(1); }
  50% { transform: translateY(-30px) rotate(180deg) scale(1.1); }
}

/* 控制面板 */
.control-panel {
  position: fixed;
  top: 20px;
  right: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  z-index: 1000;
}

.control-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: white;
  font-size: 12px;
}

.control-item:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.2);
}

.control-label {
  font-weight: 500;
  min-width: 60px;
}

.control-item .active {
  color: #ffd700;
}

/* 登录内容区域 */
.login-content {
  display: flex;
  width: 1000px;
  max-width: 100%;
  min-height: 600px;
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 
    0 25px 50px rgba(0, 0, 0, 0.25),
    0 0 0 1px rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  background: rgba(255, 255, 255, 0.1);
  z-index: 10;
  position: relative;
}

/* 左侧品牌区域 */
.brand-section {
  flex: 1;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 40px;
  position: relative;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
}

.brand-section::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(45deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.05));
  z-index: 1;
}

.brand-content {
  position: relative;
  z-index: 2;
  text-align: center;
  animation: slideInLeft 1s ease-out;
}

@keyframes slideInLeft {
  from {
    opacity: 0;
    transform: translateX(-50px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.logo-container {
  position: relative;
  margin-bottom: 2rem;
  display: inline-block;
}

.logo-icon {
  position: relative;
  z-index: 4;
  animation: pulse 2s ease-in-out infinite;
  filter: drop-shadow(0 0 10px rgba(255, 255, 255, 0.5));
}

.logo-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 80px;
  height: 80px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.3) 0%, transparent 70%);
  border-radius: 50%;
  animation: glow 2s ease-in-out infinite alternate;
  z-index: 2;
}

.logo-rings {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 1;
}

.ring {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  border: 2px solid rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  animation: ringPulse 3s ease-in-out infinite;
}

.ring-1 {
  width: 100px;
  height: 100px;
  animation-delay: 0s;
}

.ring-2 {
  width: 120px;
  height: 120px;
  animation-delay: 1s;
}

.ring-3 {
  width: 140px;
  height: 140px;
  animation-delay: 2s;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

@keyframes glow {
  from { opacity: 0.5; transform: translate(-50%, -50%) scale(1); }
  to { opacity: 1; transform: translate(-50%, -50%) scale(1.2); }
}

@keyframes ringPulse {
  0%, 100% { 
    opacity: 0.2; 
    transform: translate(-50%, -50%) scale(1);
  }
  50% { 
    opacity: 0.6; 
    transform: translate(-50%, -50%) scale(1.1);
  }
}

.brand-title {
  margin-bottom: 1.5rem;
  line-height: 1.2;
}

.title-main {
  display: block;
  font-size: 3rem;
  font-weight: 800;
  background: linear-gradient(45deg, #fff, #f0f0f0);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 0.5rem;
}

.title-sub {
  display: block;
  font-size: 1.5rem;
  font-weight: 300;
  color: rgba(255, 255, 255, 0.8);
}

.brand-description {
  font-size: 1.1rem;
  opacity: 0.9;
  max-width: 350px;
  margin: 0 auto 2rem;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.6;
}

.feature-list {
  display: flex;
  justify-content: center;
  gap: 2rem;
  margin-top: 2rem;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
  color: rgba(255, 255, 255, 0.8);
  transition: all 0.3s ease;
}

.feature-item:hover {
  color: white;
  transform: translateY(-2px);
}

/* 右侧表单区域 */
.form-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  box-shadow: 
    0 20px 40px rgba(0, 0, 0, 0.1),
    0 0 0 1px rgba(255, 255, 255, 0.2);
  animation: slideInRight 1s ease-out;
}

@keyframes slideInRight {
  from {
    opacity: 0;
    transform: translateX(50px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.card-header {
  text-align: center;
  margin-bottom: 2rem;
}

.welcome-title {
  font-size: 2rem;
  font-weight: 700;
  color: #2d3748;
  margin-bottom: 0.5rem;
  background: linear-gradient(45deg, #667eea, #764ba2);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.welcome-subtitle {
  color: #718096;
  font-size: 1rem;
  margin: 0;
}

.form-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.input-group {
  position: relative;
}

.input-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.input-label {
  font-size: 0.9rem;
  color: #4a5568;
  font-weight: 600;
}

.error-message {
  font-size: 0.8rem;
  color: #e53e3e;
  animation: shake 0.5s ease-in-out;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-5px); }
  75% { transform: translateX(5px); }
}

.input-wrapper {
  position: relative;
}

.input-glow {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  border-radius: 12px;
  background: linear-gradient(45deg, #667eea, #764ba2);
  opacity: 0;
  transition: opacity 0.3s ease;
  z-index: -1;
  filter: blur(10px);
}

.custom-input:focus-within + .input-glow {
  opacity: 0.3;
}

.custom-input {
  transition: all 0.3s ease;
  border-radius: 12px;
  border: 2px solid transparent;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  position: relative;
  z-index: 1;
}

.custom-input:focus-within {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
  transform: translateY(-2px);
  background: rgba(255, 255, 255, 0.95);
}

.input-icon {
  color: #a0aec0;
  transition: all 0.3s ease;
}

.custom-input:focus-within .input-icon {
  color: #667eea;
  transform: scale(1.1);
}

.password-toggle {
  color: #a0aec0;
  cursor: pointer;
  transition: all 0.3s ease;
  padding: 4px;
  border-radius: 4px;
}

.password-toggle:hover {
  color: #667eea;
  background: rgba(102, 126, 234, 0.1);
}

.additional-links {
  display: flex;
  justify-content: flex-end;
  margin-top: -0.5rem;
}

.forgot-password {
  font-size: 0.9rem;
  color: #667eea;
  transition: all 0.3s ease;
}

.forgot-password:hover {
  color: #764ba2;
  transform: translateY(-1px);
}

.action-buttons {
  margin-top: 1rem;
}

.login-button {
  height: 48px;
  font-size: 1rem;
  font-weight: 600;
  border-radius: 12px;
  background: linear-gradient(45deg, #667eea, #764ba2);
  border: none;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.button-shine {
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  transition: left 0.6s ease;
}

.login-button:hover .button-shine {
  left: 100%;
}

.login-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 25px rgba(102, 126, 234, 0.3);
  background: linear-gradient(45deg, #764ba2, #667eea);
}

.login-button:active {
  transform: translateY(0);
}

.login-button::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  transition: all 0.3s ease;
}

.login-button:hover::after {
  width: 300px;
  height: 300px;
}

.success-animation {
  animation: successPulse 0.6s ease-in-out;
}

@keyframes successPulse {
  0% { transform: scale(1); }
  50% { transform: scale(1.05); }
  100% { transform: scale(1); }
}

.register-prompt {
  text-align: center;
  font-size: 0.9rem;
  color: #718096;
  margin-top: 1rem;
  line-height: 1.5;
}

.register-link {
  font-weight: 600;
  transition: all 0.3s ease;
}

.register-link:hover {
  transform: translateY(-1px);
}

/* 暗黑模式适配 */
html.dark .login-container {
  background: linear-gradient(135deg, #0f0f23 0%, #1a1a2e 50%, #16213e 100%);
}

html.dark .form-wrapper {
  background: rgba(26, 26, 46, 0.9);
}

html.dark .login-card {
  background: rgba(26, 26, 46, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

html.dark .welcome-title {
  color: #e2e8f0;
}

html.dark .welcome-subtitle {
  color: #a0aec0;
}

html.dark .input-label {
  color: #e2e8f0;
}

html.dark .custom-input {
  background: rgba(45, 55, 72, 0.8);
  border-color: rgba(255, 255, 255, 0.1);
}

html.dark .custom-input:focus-within {
  border-color: #667eea;
  background: rgba(45, 55, 72, 0.9);
}

html.dark .register-prompt {
  color: #a0aec0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .login-content {
    flex-direction: column;
    width: 100%;
    min-height: auto;
    border-radius: 16px;
  }

  .brand-section {
    padding: 40px 20px;
    min-height: 200px;
  }

  .form-wrapper {
    padding: 30px 20px;
  }

  .title-main {
    font-size: 2.5rem;
  }

  .title-sub {
    font-size: 1.2rem;
  }

  .feature-list {
    flex-direction: column;
    gap: 1rem;
  }

  .welcome-title {
    font-size: 1.5rem;
  }

  .login-card {
    max-width: 100%;
  }
}

@media (max-width: 480px) {
  .login-container {
    padding: 10px;
  }

  .brand-section {
    padding: 30px 15px;
  }

  .form-wrapper {
    padding: 20px 15px;
  }

  .title-main {
    font-size: 2rem;
  }

  .welcome-title {
    font-size: 1.3rem;
  }
}

/* 加载动画 */
.login-button[loading] {
  position: relative;
}

.login-button[loading]::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 20px;
  height: 20px;
  border: 2px solid transparent;
  border-top: 2px solid white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: translate(-50%, -50%) rotate(0deg); }
  100% { transform: translate(-50%, -50%) rotate(360deg); }
}
</style>
