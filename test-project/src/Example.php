<?php

namespace App;

class Example
{
    public function processData(array $data): array
    {
        // Line 10 - has comment about extraction
        $result = [];
        foreach ($data as $item) {
            if (is_string($item)) {
                $result[] = strtoupper($item);
            }
        }
        return $result;
    }

    public function fetchRecord(int $id): ?array
    {
        $db = $this->getDatabase();

        // Line 25 - has comment about error handling
        $record = $db->find($id);

        return $record;
    }

    private function getDatabase(): object
    {
        return new \stdClass();
    }
}
