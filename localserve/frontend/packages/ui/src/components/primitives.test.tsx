// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Button, Toggle } from "./primitives";

describe("UI primitives", () => {
  it("uses button semantics by default", () => {
    render(<Button>Continue</Button>);
    expect(screen.getByRole("button", { name: "Continue" })).toHaveAttribute("type", "button");
  });
  it("exposes toggle state to assistive technology", () => {
    const change = vi.fn();
    render(<Toggle checked={false} onChange={change} label="Go online"/>);
    fireEvent.click(screen.getByRole("switch", { name: "Go online" }));
    expect(change).toHaveBeenCalledWith(true);
  });
});
