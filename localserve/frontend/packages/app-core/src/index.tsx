"use client";

import { configureStore, createSlice, type PayloadAction } from "@reduxjs/toolkit";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "next-themes";
import { useState, type PropsWithChildren } from "react";
import { Provider, useDispatch, useSelector } from "react-redux";

type UiState = { sidebarOpen: boolean; online: boolean };
const uiSlice = createSlice({
  name: "ui",
  initialState: { sidebarOpen: false, online: true } as UiState,
  reducers: {
    setSidebarOpen: (state, action: PayloadAction<boolean>) => { state.sidebarOpen = action.payload; },
    setOnline: (state, action: PayloadAction<boolean>) => { state.online = action.payload; },
  },
});

export const uiActions = uiSlice.actions;
export const createAppStore = () => configureStore({ reducer: { ui: uiSlice.reducer } });
export type AppStore = ReturnType<typeof createAppStore>;
export type RootState = ReturnType<AppStore["getState"]>;
export type AppDispatch = AppStore["dispatch"];
export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();

export function AppProviders({ children }: PropsWithChildren) {
  const [store] = useState(createAppStore);
  const [queryClient] = useState(() => new QueryClient({
    defaultOptions: {
      queries: { staleTime: 30_000, gcTime: 10 * 60_000, retry: (count, error) => count < 2 && isRetryable(error) },
      mutations: { retry: false },
    },
  }));
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
      <Provider store={store}><QueryClientProvider client={queryClient}>{children}</QueryClientProvider></Provider>
    </ThemeProvider>
  );
}

function isRetryable(error: unknown): boolean {
  if (typeof error !== "object" || error === null || !("status" in error)) return false;
  return [502, 503, 504].includes(Number((error as { status: unknown }).status));
}
