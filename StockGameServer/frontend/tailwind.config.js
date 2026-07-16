/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{vue,js}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#f2f7f5",
          100: "#d9e9e0",
          200: "#b5d2c1",
          300: "#88b59c",
          400: "#5f9678",
          500: "#457f62",
          600: "#32644d",
          700: "#294f3f",
          800: "#243f34",
          900: "#20342d"
        }
      },
      boxShadow: {
        bloom: "0 20px 80px rgba(16, 185, 129, 0.12)"
      }
    }
  },
  plugins: []
};
