module.exports = {
  content: ["./src/**/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50:  "#fffbeb",
          100: "#fef3c7",
          400: "#fbbf24",
          500: "#f59e0b",
          600: "#d97706",
        }
      },
      fontFamily: {
        mono: ["'JetBrains Mono'", "Menlo", "Monaco", "monospace"],
      }
    }
  },
  plugins: []
};
