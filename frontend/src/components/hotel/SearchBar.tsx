"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

interface SearchBarProps {
    defaultCity?: string;
}

export function SearchBar({defaultCity = ""}: SearchBarProps) {
    const [city, setCity] = useState(defaultCity);
    const router = useRouter();

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        const params = new URLSearchParams();
        if (city.trim()) params.set("city", city.trim());
        router.push(`/?${params.toString()}`);
    };

    return (
        <form onSubmit={handleSearch} className="flex w-full max-w-lg gap-2">
            <div className="relative flex-1">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"/>
                <Input
                    placeholder="Search by city…"
                    value={city}
                    onChange={(e) => setCity(e.target.value)}
                    className="pl-9"
                />
            </div>
            <Button type="submit">Search</Button>
        </form>
    );
}
